package com.todo.controller;

import com.todo.dto.response.ApiResponse;
import com.todo.dto.response.NotificationResponse;
import com.todo.entity.User;
import com.todo.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/unread")
    public ApiResponse<List<NotificationResponse>> unread(@AuthenticationPrincipal User user) {
        return ApiResponse.ok(notificationService.listUnread(user.getId()));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Integer>> unreadCount(@AuthenticationPrincipal User user) {
        return ApiResponse.ok(Collections.singletonMap("count", notificationService.unreadCount(user.getId())));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Void> read(@AuthenticationPrincipal User user, @PathVariable Long id) {
        notificationService.markRead(id, user.getId());
        return ApiResponse.ok(null);
    }

    @PutMapping("/read-all")
    public ApiResponse<Void> readAll(@AuthenticationPrincipal User user) {
        notificationService.markAllRead(user.getId());
        return ApiResponse.ok(null);
    }
}