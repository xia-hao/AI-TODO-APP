package com.todo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("comments")
public class Comment extends BaseEntity {
    private Long todoId;
    private Long userId;
    private String content;
    private Long parentId;

    @TableField(exist = false)
    private String username;
    @TableField(exist = false)
    private String displayName;
}
