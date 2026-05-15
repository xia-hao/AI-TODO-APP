package com.todo.dto.request;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;

@Data
public class ProjectRequest {
    @NotBlank(message = "项目名称不能为空")
    @Size(max = 100, message = "项目名称不能超过100字符")
    private String name;
    private String description;
    private String color;
    private String icon;
    private List<Long> teamIds;  // 若提供，则关联指定团队（多选）
}