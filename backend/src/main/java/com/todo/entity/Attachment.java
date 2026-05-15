package com.todo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("attachments")
public class Attachment extends BaseEntity {
    private Long todoId;
    private Long userId;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String mimeType;
}
