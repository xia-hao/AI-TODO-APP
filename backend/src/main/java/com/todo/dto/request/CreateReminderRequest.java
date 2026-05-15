package com.todo.dto.request;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class CreateReminderRequest {
    @NotBlank(message = "提醒时间不能为空")
    private String remindAt;
}
