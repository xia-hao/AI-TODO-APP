package com.todo.dto.request;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class SubtaskRequest {
    @NotBlank(message = "子任务内容不能为空")
    @Size(max = 500, message = "子任务内容不能超过500字符")
    private String text;
    private Long assigneeId;
    private String dueDate;    // 接收格式 yyyy-MM-dd
}