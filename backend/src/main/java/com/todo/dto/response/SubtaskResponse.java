package com.todo.dto.response;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class SubtaskResponse extends BaseResponse {
    private Long todoId;
    private String text;
    private Boolean completed;
    private Integer sortOrder;
    private Long assigneeId;
    private String assigneeName;
    private LocalDate dueDate;
}
