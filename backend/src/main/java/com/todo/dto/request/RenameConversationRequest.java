package com.todo.dto.request;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RenameConversationRequest {
    @NotBlank(message = "标题不能为空")
    private String title;
}
