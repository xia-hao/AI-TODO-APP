package com.todo.dto.request;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
public class CreateTeamTagRequest {
    @NotNull(message = "团队ID不能为空")
    private Long teamId;
    @NotBlank(message = "标签名称不能为空")
    @Size(max = 50, message = "标签名称不能超过50字符")
    private String name;
    private String color;
}
