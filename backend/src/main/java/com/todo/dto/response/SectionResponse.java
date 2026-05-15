package com.todo.dto.response;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class SectionResponse extends BaseResponse {
    private Long projectId;
    private String name;
    private Integer sortOrder;
}
