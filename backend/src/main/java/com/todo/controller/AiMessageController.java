package com.todo.controller;

import com.todo.dto.request.SaveMessageRequest;
import com.todo.dto.response.ApiResponse;
import com.todo.entity.AiMessage;
import com.todo.entity.User;
import com.todo.service.AiMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.util.List;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Validated
public class AiMessageController {

    private final AiMessageService aiMessageService;

    @GetMapping("/messages")
    public ApiResponse<List<AiMessage>> getMessages(@RequestParam @NotBlank(message = "会话ID不能为空") String sessionId) {
        return ApiResponse.ok(aiMessageService.getMessages(sessionId));
    }
}
