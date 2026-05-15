package com.todo.dto.response;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class AttachmentResponse extends BaseResponse {
    private Long todoId;
    private String fileName;
    private Long fileSize;
    private String mimeType;
}
