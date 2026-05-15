package com.todo.dto.response;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ReportResponse {
    private Long id;
    private Long userId;
    private String type;
    private String scope;
    private Long teamId;
    private String teamName;
    private String title;
    private String preview;
    private String content;
    private Object jsonData;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private LocalDateTime createTime;
}
