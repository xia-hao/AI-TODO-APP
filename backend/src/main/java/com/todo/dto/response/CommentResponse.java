package com.todo.dto.response;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class CommentResponse extends BaseResponse {
    private Long todoId;
    private Long userId;
    private String username;
    private String displayName;
    private String content;
    private Long parentId;
    private List<CommentResponse> children;
}
