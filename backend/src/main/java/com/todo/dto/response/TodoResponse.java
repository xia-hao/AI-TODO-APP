package com.todo.dto.response;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class TodoResponse extends BaseResponse {
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
    private String sectionName;
    private String assigneeName;
    private String projectName;
    private String teamName;
    private List<TagResponse> tags;
    private LocalDateTime deletedTime;
}
