package com.todo.dto.request;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class UpdateMemberRoleRequest {
    @NotBlank(message = "角色不能为空")
    private String role;
}
