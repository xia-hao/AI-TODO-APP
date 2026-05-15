package com.todo.dto.request;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class ImportItem {
    @NotBlank(message = "文本内容不能为空")
    private String text;
    private String category;
    private String priority;
    private String dueDate;
    private Boolean completed;
}
