package com.todo.controller;

import com.todo.dto.request.CreateReminderRequest;
import com.todo.dto.response.ApiResponse;
import com.todo.entity.Reminder;
import com.todo.entity.User;
import com.todo.service.ReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/todos/{todoId}/reminders")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;

    @PostMapping
    public ApiResponse<Reminder> create(@AuthenticationPrincipal User user,
                                        @PathVariable Long todoId,
                                        @Valid @RequestBody CreateReminderRequest req) {
        LocalDateTime remindAt = LocalDateTime.parse(req.getRemindAt());
        return ApiResponse.ok(reminderService.create(todoId, user.getId(), remindAt), "提醒设置成功");
    }

    @DeleteMapping("/{reminderId}")
    public ApiResponse<Void> cancel(@AuthenticationPrincipal User user,
                                    @PathVariable Long todoId,
                                    @PathVariable Long reminderId) {
        reminderService.cancel(reminderId, user.getId());
        return ApiResponse.ok(null, "提醒已取消");
    }
}