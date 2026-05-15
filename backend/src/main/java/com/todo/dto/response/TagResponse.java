package com.todo.dto.response;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class TagResponse extends BaseResponse {
    private String name;
    private String color;
    private Long ownerId;
    private Long teamId;
    private Long projectId;
}
