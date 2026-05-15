package com.todo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tags")
public class Tag extends BaseEntity {
    private String name;
    private String color;
    private Long ownerId;
    private Long teamId;
    private Long projectId;
}
