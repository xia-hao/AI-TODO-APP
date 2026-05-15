package com.todo.dto.request;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class SaveMessageRequest {
    @NotBlank(message = "会话ID不能为空")
    private String sessionId;
    @NotBlank(message = "角色不能为空")
    private String role;
    @NotBlank(message = "内容不能为空")
    private String content;
}
