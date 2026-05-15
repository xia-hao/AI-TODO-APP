package com.todo.controller;

import com.todo.dto.request.AiChatRequest;
import com.todo.dto.request.RenameConversationRequest;
import com.todo.dto.response.ApiResponse;
import com.todo.entity.User;
import com.todo.entity.AiConversation;
import com.todo.entity.AiMessage;
import com.todo.service.AiConversationService;
import com.todo.service.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import javax.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AiController {

    private final AiService aiService;
    private final AiConversationService aiConversationService;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@AuthenticationPrincipal User user,
                           @Valid @RequestBody AiChatRequest request) {
        log.info("AI chat request from user {}: {}", user.getId(), request.getMessage());
        return aiService.chatStream(request, user);
    }

    @PostMapping("/session")
    public ApiResponse<Map<String, String>> createSession() {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        return ApiResponse.ok(Collections.singletonMap("sessionId", sessionId));
    }

    @GetMapping("/conversations")
    public ApiResponse<List<AiConversation>> listConversations(@AuthenticationPrincipal User user) {
        return ApiResponse.ok(aiConversationService.listByUser(user.getId()));
    }

    @PostMapping("/conversations")
    public ApiResponse<AiConversation> createConversation(@AuthenticationPrincipal User user) {
        return ApiResponse.ok(aiConversationService.create(user.getId()));
    }

    @DeleteMapping("/conversations/{id}")
    public ApiResponse<Void> deleteConversation(@AuthenticationPrincipal User user,
                                                 @PathVariable Long id) {
        aiConversationService.delete(id, user.getId());
        return ApiResponse.ok(null, "已删除");
    }

    @PutMapping("/conversations/{id}/rename")
    public ApiResponse<Void> renameConversation(@AuthenticationPrincipal User user,
                                                 @PathVariable Long id,
                                                 @Valid @RequestBody RenameConversationRequest req) {
        aiConversationService.rename(id, user.getId(), req.getTitle().trim());
        return ApiResponse.ok(null, "已重命名");
    }

    @GetMapping("/conversations/{id}/messages")
    public ApiResponse<List<AiMessage>> getConversationMessages(@AuthenticationPrincipal User user,
                                                                 @PathVariable Long id) {
        return ApiResponse.ok(aiConversationService.getMessages(id, user.getId()));
    }
}
