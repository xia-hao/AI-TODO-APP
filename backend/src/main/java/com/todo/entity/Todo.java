package com.todo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("todos")
public class Todo extends BaseEntity {
    private String text;
    private Boolean completed;
    private String category;
    private String priority;
    private LocalDate dueDate;
    private Integer sortOrder;
    private Long ownerId;
    private Long teamId;
    private Long assigneeId;
    private Long projectId;
    private Long sectionId;
    private LocalDateTime deletedTime;
}
