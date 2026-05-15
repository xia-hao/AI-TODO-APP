package com.todo.controller;

import com.todo.dto.response.ActivityLogResponse;
import com.todo.dto.response.ApiResponse;
import com.todo.entity.User;
import com.todo.service.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/activities")
@RequiredArgsConstructor
public class ActivityLogController {

    private final ActivityLogService activityLogService;

    @GetMapping
    public ApiResponse<List<ActivityLogResponse>> list(@AuthenticationPrincipal User user,
                                                        @PathVariable Long projectId,
                                                        @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(activityLogService.getByProject(projectId, user.getId(), limit));
    }
}
