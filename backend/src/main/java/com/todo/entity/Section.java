package com.todo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sections")
public class Section extends BaseEntity {
    private Long projectId;
    private String name;
    private Integer sortOrder;
}
