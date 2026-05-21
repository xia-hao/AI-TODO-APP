package com.todo.service;

import com.todo.config.AiConfig;
import com.todo.dto.request.AiChatRequest;
import com.todo.dto.request.ConfirmActionRequest;
import com.todo.dto.response.ApiResponse;
import com.todo.entity.AiConversation;
import com.todo.entity.AiMessage;
import com.todo.entity.User;
import com.todo.security.JwtTokenProvider;
import com.todo.service.AiConversationService;
import com.todo.util.HttpClientUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiService {

    private final AiConfig aiConfig;
    private final AiMessageService aiMessageService;
    private final AiConversationService aiConversationService;
    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public SseEmitter chatStream(AiChatRequest request, User user) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout

        // 保存用户消息
        String sessionId;
        if (request.getConversationId() != null) {
            sessionId = String.valueOf(request.getConversationId());
        } else {
            sessionId = request.getSessionId() != null ? request.getSessionId() : UUID.randomUUID().toString().replace("-", "");
        }
        String message = request.getMessage() != null ? request.getMessage() : "";
        aiMessageService.saveMessage(user.getId(), sessionId, "user", message);

        final Long convId = request.getConversationId();
        final AtomicReference<HttpURLConnection> connRef = new AtomicReference<>();

        // 客户端断开/完成/超时时，立即切断 ai-service 的连接，让 SSE 流快速停止
        emitter.onCompletion(() -> {
            HttpURLConnection c = connRef.getAndSet(null);
            if (c != null) c.disconnect();
        });
        emitter.onTimeout(() -> {
            HttpURLConnection c = connRef.getAndSet(null);
            if (c != null) c.disconnect();
        });

        executor.execute(() -> {
            HttpURLConnection conn = null;
            StringBuilder assistantContent = new StringBuilder();
            try {
                Map<String, Object> body = new HashMap<>();
                body.put("session_id", sessionId);
                body.put("message", message);
                body.put("token", jwtTokenProvider.generateScopedToken(user.getId(), user.getUsername()));
                body.put("username", user.getUsername());
                body.put("display_name", user.getDisplayName() != null ? user.getDisplayName() : user.getUsername());

                // 从数据库加载对话历史记录并作为上下文传递（在ai服务重启后仍然有效）
                List<AiMessage> history = aiMessageService.getMessages(sessionId);
                // 只保留最后10个回合（用户+助手对），以避免上下文溢出
                int maxMessages = 20;
                if (history.size() > maxMessages) {
                    history = history.subList(history.size() - maxMessages, history.size());
                }
                List<Map<String, String>> messagesList = new ArrayList<>();
                for (AiMessage msg : history) {
                    Map<String, String> m = new HashMap<>();
                    m.put("role", msg.getRole());
                    m.put("content", msg.getContent());
                    messagesList.add(m);
                }
                body.put("messages", messagesList);

                String aiUrl = aiConfig.getServiceUrl() + "/api/chat";
                Map<String, String> chatHeaders = new HashMap<>();
                if (aiConfig.getInternalApiKey() != null) {
                    chatHeaders.put("X-Api-Key", aiConfig.getInternalApiKey());
                }
                conn = HttpClientUtils.openConnection("POST", aiUrl, chatHeaders, body, 10000, 120000);
                connRef.set(conn);

                int status = conn.getResponseCode();
                if (status != 200) {
                    String errorBody = HttpClientUtils.readStream(conn.getErrorStream());
                    sendError(emitter, assistantContent, "AI 服务错误 (" + status + "): " + errorBody);
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        String data = line.substring(6).trim();
                        if (!data.isEmpty()) {
                            // 累积 AI 回复内容
                            try {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> parsed = objectMapper.readValue(data, Map.class);
                                if ("text".equals(parsed.get("type")) || "token".equals(parsed.get("type"))) {
                                    Object content = parsed.get("content");
                                    if (content != null) assistantContent.append(content.toString());
                                }
                                // 不转发 ai-service 的 done 事件 — 标题生成完成后我们会发自己的 done
                                if ("done".equals(parsed.get("type"))) {
                                    continue;
                                }
                            } catch (Exception ignored) {
                            }
                            emitter.send(SseEmitter.event()
                                    .name("message")
                                    .data(data));
                        }
                    } else if (line.startsWith("event: ")) {
                        // Forward event type - already handled above
                    }
                    // Skip empty lines (SSE delimiter)
                }

                // Save assistant message and generate title before done event
                String reply = assistantContent.toString();
                saveAssistantMessage(user.getId(), sessionId, reply);
                if (convId != null && !reply.isEmpty()) {
                    try {
                        AiConversation conv = aiConversationService.getById(convId, user.getId());
                        if ("新对话".equals(conv.getTitle())) {
                            String title = generateTitle(reply.length() > 200 ? reply.substring(0, 200) : reply);
                            if (title != null && !title.isEmpty()) {
                                aiConversationService.updateTitle(convId, title);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to generate title for conversation {}", convId, e);
                    }
                }

                emitter.send(SseEmitter.event().name("done").data("{\"type\":\"done\"}"));
                emitter.complete();

            } catch (Exception e) {
                log.error("AI chat stream error", e);
                sendError(emitter, assistantContent, "请求处理失败: " + e.getMessage());
            } finally {
                connRef.set(null);
                if (conn != null) conn.disconnect();
            }
        });

        return emitter;
    }

    private void sendError(SseEmitter emitter, StringBuilder assistantContent, String error) {
        assistantContent.append(error);
        try {
            Map<String, String> errorData = new HashMap<>();
            errorData.put("type", "error");
            errorData.put("content", error);
            emitter.send(SseEmitter.event().name("message").data(objectMapper.writeValueAsString(errorData)));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void saveAssistantMessage(Long userId, String sessionId, String content) {
        if (!content.isEmpty()) {
            aiMessageService.saveMessage(userId, sessionId, "assistant", content);
        }
    }

    private String generateTitle(String message) {
        try {
            String url = aiConfig.getServiceUrl() + "/api/generate-title";
            Map<String, String> body = new HashMap<>();
            body.put("message", message);
            Map<String, String> headers = new HashMap<>();
            if (aiConfig.getInternalApiKey() != null) {
                headers.put("X-Api-Key", aiConfig.getInternalApiKey());
            }
            Map<String, Object> resp = HttpClientUtils.postJson(url, headers, body);
            Object title = resp.get("title");
            return title != null ? title.toString() : "新对话";
        } catch (Exception e) {
            log.warn("Title generation failed", e);
            return null;
        }
    }

    public ApiResponse<Map<String, Object>> confirmAction(ConfirmActionRequest request, User user) {
        String url = aiConfig.getServiceUrl() + "/api/chat/confirm-action";
        Map<String, String> headers = new HashMap<>();
        if (aiConfig.getInternalApiKey() != null) {
            headers.put("X-Api-Key", aiConfig.getInternalApiKey());
        }
        Map<String, Object> body = new HashMap<>();
        body.put("confirm_id", request.getConfirmId());
        body.put("approved", request.isApproved());
        // 传入新 token，ai-service 会在后续工具调用中使用，防止长时间对话 token 过期
        body.put("token", jwtTokenProvider.generateScopedToken(user.getId(), user.getUsername()));
        Map<String, Object> resp = HttpClientUtils.postJson(url, headers, body);
        if (resp.containsKey("error")) {
            return ApiResponse.ok(null, "确认操作失败");
        }
        return ApiResponse.ok(resp);
    }
}
