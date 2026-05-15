package com.todo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.todo.dto.response.ActivityLogResponse;
import com.todo.entity.ActivityLog;
import com.todo.entity.User;
import com.todo.mapper.ActivityLogMapper;
import com.todo.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private final ActivityLogMapper activityLogMapper;
    private final UserMapper userMapper;
    private final ProjectService projectService;

    @Async
    public void log(Long projectId, Long userId, String action, String targetType, Long targetId, String detail) {
        ActivityLog log = new ActivityLog();
        log.setProjectId(projectId);
        log.setUserId(userId);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detail != null && detail.length() > 500 ? detail.substring(0, 500) : detail);
        activityLogMapper.insert(log);
    }

    public List<ActivityLogResponse> getByProject(Long projectId, Long userId, int limit) {
        projectService.getDetail(projectId, userId); // permission check
        List<ActivityLog> logs = activityLogMapper.selectList(new LambdaQueryWrapper<ActivityLog>()
                .eq(ActivityLog::getProjectId, projectId)
                .orderByDesc(ActivityLog::getCreateTime)
                .last("LIMIT " + limit));
        Set<Long> userIds = logs.stream().map(ActivityLog::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                        .filter(u -> u != null)
                        .collect(Collectors.toMap(User::getId, u -> u));
        return logs.stream().map(log -> toResponse(log, userMap)).collect(Collectors.toList());
    }

    private ActivityLogResponse toResponse(ActivityLog log) {
        User user = userMapper.selectById(log.getUserId());
        return toResponse(log, user != null ? Collections.singletonMap(user.getId(), user) : Collections.emptyMap());
    }

    private ActivityLogResponse toResponse(ActivityLog log, Map<Long, User> userMap) {
        ActivityLogResponse r = new ActivityLogResponse();
        r.setId(log.getId());
        r.setProjectId(log.getProjectId());
        r.setUserId(log.getUserId());
        r.setAction(log.getAction());
        r.setTargetType(log.getTargetType());
        r.setTargetId(log.getTargetId());
        r.setDetail(log.getDetail());
        r.setCreateTime(log.getCreateTime());
        User user = userMap.get(log.getUserId());
        r.setUserDisplayName(user != null ? user.getDisplayName() : "未知用户");
        return r;
    }
}
