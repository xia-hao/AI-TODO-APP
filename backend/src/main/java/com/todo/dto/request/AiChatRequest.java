package com.todo.dto.request;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class AiChatRequest {
    @NotBlank(message = "消息不能为空")
    private String message;
    private String sessionId;
    private Long conversationId;
}
