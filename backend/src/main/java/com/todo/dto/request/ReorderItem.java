package com.todo.dto.request;

import lombok.Data;
import javax.validation.constraints.NotNull;

@Data
public class ReorderItem {
    @NotNull(message = "ID不能为空")
    private Long id;
    @NotNull(message = "排序号不能为空")
    private Integer sortOrder;
}
