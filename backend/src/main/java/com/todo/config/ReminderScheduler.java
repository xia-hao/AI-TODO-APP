package com.todo.config;

import com.todo.entity.Reminder;
import com.todo.service.ReminderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReminderScheduler {

    private final ReminderService reminderService;

    @Scheduled(fixedDelay = 60000) // 每分钟检查一次
    public void sendReminders() {
        List<Reminder> pending = reminderService.getPendingToSend();
        for (Reminder reminder : pending) {
            try {
                // 未来扩展：发送邮件/站内通知，目前只标记已发送
                reminderService.markSent(reminder);
                log.info("Reminder sent for todo: {} user: {}", reminder.getTodoId(), reminder.getUserId());
            } catch (Exception e) {
                log.error("Error processing reminder {}", reminder.getId(), e);
            }
        }
    }
}