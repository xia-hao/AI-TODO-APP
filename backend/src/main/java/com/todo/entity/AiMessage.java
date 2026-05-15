package com.todo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_messages")
public class AiMessage extends BaseEntity {
    private String sessionId;
    private Long userId;
    private String role;
    private String content;
}
