package com.todo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("team_members")
public class TeamMember extends BaseEntity {
    private Long teamId;
    private Long userId;
    private String role;
    private LocalDateTime joinedAt;
}
