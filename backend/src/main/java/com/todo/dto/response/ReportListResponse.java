package com.todo.dto.response;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ReportListResponse {
    private Long id;
    private String title;
    private String type;
    private String scope;
    private Long teamId;
    private String teamName;
    private String preview;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private LocalDateTime createTime;
}
