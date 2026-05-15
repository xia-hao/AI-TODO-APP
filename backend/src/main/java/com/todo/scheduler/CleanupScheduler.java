package com.todo.scheduler;

import com.todo.mapper.TodoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupScheduler {

    private final TodoMapper todoMapper;

    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupDeletedTodos() {
        LocalDateTime deadline = LocalDateTime.now().minusDays(30);
        int count = todoMapper.forceDeleteExpired(deadline);
        if (count > 0) {
            log.info("已清理 {} 条过期回收站待办（超过30天）", count);
        }
    }
}
