package com.todo.dto.request;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class ConfirmActionRequest {
    @NotBlank
    private String confirmId;
    private boolean approved;
}
