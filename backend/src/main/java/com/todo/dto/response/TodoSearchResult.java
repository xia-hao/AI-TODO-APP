package com.todo.dto.response;

import lombok.Data;
import java.time.LocalDate;

@Data
public class TodoSearchResult {
    private Long id;
    private String text;
    private Long projectId;
    private String projectName;
    private Boolean completed;
    private LocalDate dueDate;
    private Long teamId;
    private String teamName;
    private Long sectionId;
    private String sectionName;
    private Long assigneeId;
    private String assigneeName;
}
