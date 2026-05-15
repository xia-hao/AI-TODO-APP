package com.todo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reminders")
public class Reminder extends BaseEntity {
    private Long todoId;
    private Long userId;
    private LocalDateTime remindAt;
    private Boolean isSent;
    private LocalDateTime sentAt;
}
