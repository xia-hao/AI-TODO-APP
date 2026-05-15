package com.todo.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.todo.entity.Todo;
import com.todo.entity.User;
import com.todo.mapper.TodoMapper;
import com.todo.mapper.UserMapper;
import com.todo.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReportScheduler {

    private final ReportService reportService;
    private final UserMapper userMapper;
    private final TodoMapper todoMapper;

    @Scheduled(cron = "0 30 8 * * 1-5")
    public void generateDailyReports() {
        generateForActiveUsers("DAILY");
    }

    @Scheduled(cron = "0 0 9 * * 1")
    public void generateWeeklyReports() {
        generateForActiveUsers("WEEKLY");
    }

    private void generateForActiveUsers(String type) {
        List<User> activeUsers = findActiveUsers();
        log.info("Starting {} report generation for {} active users", type, activeUsers.size());

        for (User user : activeUsers) {
            try {
                reportService.generateAll(user.getId(), type);
                log.debug("Generated {} report for user {}", type, user.getId());
            } catch (Exception e) {
                log.warn("Failed to generate {} report for user {}: {}", type, user.getId(), e.getMessage());
            }
        }

        log.info("Completed {} report generation", type);
    }

    private List<User> findActiveUsers() {
        List<Long> activeUserIds = todoMapper.selectList(
                new LambdaQueryWrapper<Todo>()
                        .select(Todo::getOwnerId)
                        .eq(Todo::getCompleted, false)
                        .eq(Todo::getDeleted, 0)
                        .groupBy(Todo::getOwnerId)
        ).stream().map(Todo::getOwnerId).distinct().collect(Collectors.toList());

        if (activeUserIds.isEmpty()) return Collections.emptyList();

        return userMapper.selectBatchIds(activeUserIds).stream()
                .filter(u -> u.getDeleted() == 0)
                .collect(Collectors.toList());
    }
}
