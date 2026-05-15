package com.todo.dto.request;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class CreateProjectTagRequest {
    @NotBlank(message = "标签名称不能为空")
    @Size(max = 50, message = "标签名称不能超过50字符")
    private String name;
    private String color;
}
