package com.todo.controller;

import com.todo.dto.response.ApiResponse;
import com.todo.dto.response.TodoResponse;
import com.todo.entity.User;
import com.todo.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview(@AuthenticationPrincipal User user) {
        return ApiResponse.ok(dashboardService.overview(user.getId()));
    }

    @GetMapping("/trends")
    public ApiResponse<List<Map<String, Object>>> trends(@AuthenticationPrincipal User user,
                                                         @RequestParam(defaultValue = "7") int days) {
        return ApiResponse.ok(dashboardService.trend(user.getId(), days));
    }

    @GetMapping("/upcoming")
    public ApiResponse<List<TodoResponse>> upcoming(@AuthenticationPrincipal User user,
                                                     @RequestParam(defaultValue = "5") int limit) {
        return ApiResponse.ok(dashboardService.upcoming(user.getId(), limit).stream()
                .map(t -> {
                    TodoResponse r = new TodoResponse();
                    r.setId(t.getId());
                    r.setText(t.getText());
                    r.setCompleted(t.getCompleted());
                    r.setPriority(t.getPriority());
                    r.setDueDate(t.getDueDate());
                    return r;
                }).collect(Collectors.toList()));
    }

    @GetMapping("/projects")
    public ApiResponse<List<Map<String, Object>>> projectStats(@AuthenticationPrincipal User user) {
        return ApiResponse.ok(dashboardService.projectStats(user.getId()));
    }

    @GetMapping("/assignees")
    public ApiResponse<List<Map<String, Object>>> assigneeStats(@AuthenticationPrincipal User user) {
        return ApiResponse.ok(dashboardService.assigneeStats(user.getId()));
    }

    @GetMapping("/tags")
    public ApiResponse<List<Map<String, Object>>> tagStats() {
        return ApiResponse.ok(dashboardService.tagUsageStats());
    }
}
