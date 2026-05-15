package com.todo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("subtasks")
public class Subtask extends BaseEntity {
    private Long todoId;
    private String text;
    private Boolean completed;
    private Integer sortOrder;
    private Long assigneeId;
    private LocalDate dueDate;
}
