package com.todo.dto.request;

import lombok.Data;
import javax.validation.constraints.NotNull;

@Data
public class MoveSectionRequest {
    @NotNull(message = "分区ID不能为空")
    private Long sectionId;
}
