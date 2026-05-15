package com.todo.dto.response;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ActivityLogResponse {
    private Long id;
    private Long projectId;
    private Long userId;
    private String userDisplayName;
    private String action;
    private String targetType;
    private Long targetId;
    private String detail;
    private LocalDateTime createTime;
}
