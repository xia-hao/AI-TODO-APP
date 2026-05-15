package com.todo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("teams")
public class Team extends BaseEntity {
    private String name;
    private String description;
    private String inviteCode;
    private Long ownerId;
}
