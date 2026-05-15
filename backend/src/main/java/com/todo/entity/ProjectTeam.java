package com.todo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("project_teams")
public class ProjectTeam extends BaseEntity {
    private Long projectId;
    private Long teamId;
}
