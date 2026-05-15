package com.todo.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public abstract class BaseResponse {
    private Long id;
    private Long createBy;
    private Long updateBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
