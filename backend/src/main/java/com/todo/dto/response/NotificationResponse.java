package com.todo.dto.response;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class NotificationResponse extends BaseResponse {
    private String type;
    private String title;
    private String content;
    private String targetUrl;
    private Boolean isRead;
}
