package com.todo.dto.request;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class SectionRequest {
    @NotBlank(message = "分区名称不能为空")
    @Size(max = 100, message = "分区名称不能超过100字符")
    private String name;
}
