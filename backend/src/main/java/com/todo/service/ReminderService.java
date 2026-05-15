package com.todo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.todo.entity.Reminder;
import com.todo.exception.AppException;
import com.todo.mapper.ReminderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReminderService {

    private final ReminderMapper reminderMapper;
    private final TodoService todoService;

    public Reminder create(Long todoId, Long userId, LocalDateTime remindAt) {
        // 校验任务权限
        todoService.getAndAssertAccess(todoId, userId, false);

        Reminder reminder = new Reminder();
        reminder.setTodoId(todoId);
        reminder.setUserId(userId);
        reminder.setRemindAt(remindAt);
        reminder.setIsSent(false);
        reminderMapper.insert(reminder);
        return reminder;
    }

    public void cancel(Long reminderId, Long userId) {
        Reminder reminder = reminderMapper.selectById(reminderId);
        if (reminder == null || !reminder.getUserId().equals(userId)) {
            throw new AppException("提醒不存在或无权操作", HttpStatus.FORBIDDEN);
        }
        reminderMapper.deleteById(reminderId);
    }

    // 获取所有待发送的提醒（供定时任务使用）
    public List<Reminder> getPendingToSend() {
        return reminderMapper.selectList(
                new LambdaQueryWrapper<Reminder>()
                        .eq(Reminder::getIsSent, false)
                        .le(Reminder::getRemindAt, LocalDateTime.now())
        );
    }

    public void markSent(Reminder reminder) {
        reminder.setIsSent(true);
        reminder.setSentAt(LocalDateTime.now());
        reminderMapper.updateById(reminder);
    }
}