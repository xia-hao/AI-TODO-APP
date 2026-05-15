package com.todo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notifications")
public class Notification extends BaseEntity {
    private Long userId;
    private String type;
    private String title;
    private String content;
    private String targetUrl;
    private Boolean isRead;
}
