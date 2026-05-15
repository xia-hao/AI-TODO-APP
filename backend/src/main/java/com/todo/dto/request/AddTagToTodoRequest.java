package com.todo.dto.request;

import lombok.Data;
import javax.validation.constraints.NotNull;

@Data
public class AddTagToTodoRequest {
    @NotNull(message = "标签ID不能为空")
    private Long tagId;
}
