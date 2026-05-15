package com.todo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("todo_tags")
public class TodoTag {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long todoId;
    private Long tagId;
}