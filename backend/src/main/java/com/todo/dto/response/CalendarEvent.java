package com.todo.dto.response;

import lombok.Data;
import java.time.LocalDate;

@Data
public class CalendarEvent {
    private Long id;
    private String title;
    private LocalDate start;
    private String color;
    private Long projectId;
    private String projectName;
    private Boolean completed;
}
