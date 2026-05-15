package com.todo.controller;

import com.todo.dto.request.*;
import com.todo.dto.response.ApiResponse;
import com.todo.dto.response.SubtaskResponse;
import com.todo.entity.User;
import com.todo.service.SubtaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import org.springframework.validation.annotation.Validated;
import java.util.List;

@RestController
@RequestMapping("/api/todos/{todoId}/subtasks")
@RequiredArgsConstructor
@Validated
public class SubtaskController {

    private final SubtaskService subtaskService;

    @GetMapping
    public ApiResponse<List<SubtaskResponse>> list(@AuthenticationPrincipal User user,
                                                   @PathVariable Long todoId) {
        return ApiResponse.ok(subtaskService.list(todoId, user.getId()));
    }

    @PostMapping
    public ApiResponse<SubtaskResponse> create(@AuthenticationPrincipal User user,
                                               @PathVariable Long todoId,
                                               @Valid @RequestBody SubtaskRequest req) {
        return ApiResponse.ok(subtaskService.create(todoId, user.getId(), req), "子任务创建成功");
    }

    @PutMapping("/{subtaskId}")
    public ApiResponse<SubtaskResponse> update(@AuthenticationPrincipal User user,
                                               @PathVariable Long todoId,
                                               @PathVariable Long subtaskId,
                                               @Valid @RequestBody SubtaskRequest req) {
        return ApiResponse.ok(subtaskService.update(subtaskId, user.getId(), req));
    }

    @PatchMapping("/{subtaskId}/complete")
    public ApiResponse<SubtaskResponse> toggleComplete(@AuthenticationPrincipal User user,
                                                       @PathVariable Long todoId,
                                                       @PathVariable Long subtaskId) {
        return ApiResponse.ok(subtaskService.toggleComplete(subtaskId, user.getId()));
    }

    @DeleteMapping("/{subtaskId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal User user,
                                    @PathVariable Long todoId,
                                    @PathVariable Long subtaskId) {
        subtaskService.delete(subtaskId, user.getId());
        return ApiResponse.ok(null, "子任务已删除");
    }

    @PutMapping("/reorder")
    public ApiResponse<Void> reorder(@AuthenticationPrincipal User user,
                                     @PathVariable Long todoId,
                                     @Valid @RequestBody List<ReorderItem> items) {
        subtaskService.reorder(todoId, user.getId(), items);
        return ApiResponse.ok(null, "排序已更新");
    }
}