package com.todo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("projects")
public class Project extends BaseEntity {
    private String name;
    private String description;
    private String color;
    private String icon;
    private Long ownerId;
    private Boolean isArchived;
    private Integer sortOrder;
}
