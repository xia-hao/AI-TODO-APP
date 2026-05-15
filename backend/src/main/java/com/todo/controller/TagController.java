package com.todo.controller;

import com.todo.dto.request.*;
import com.todo.dto.response.ApiResponse;
import com.todo.dto.response.TagResponse;
import com.todo.entity.User;
import com.todo.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import org.springframework.validation.annotation.Validated;
import java.util.List;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
@Validated
public class TagController {

    private final TagService tagService;

    // ========== 获取项目可用标签（项目标签 + 所属团队标签） ==========
    @GetMapping("/project/{projectId}")
    public ApiResponse<List<TagResponse>> listForProject(@AuthenticationPrincipal User user,
                                                         @PathVariable Long projectId) {
        return ApiResponse.ok(tagService.listForProject(projectId, user.getId()));
    }

    // ========== 创建项目标签 ==========
    @PostMapping("/project/{projectId}")
    public ApiResponse<TagResponse> createProjectTag(@AuthenticationPrincipal User user,
                                                     @PathVariable Long projectId,
                                                     @Valid @RequestBody CreateProjectTagRequest req) {
        String color = req.getColor() != null ? req.getColor() : "#409eff";
        return ApiResponse.ok(tagService.createProjectTag(user.getId(), projectId, req.getName(), color),
                "项目标签已创建");
    }

    // ========== 获取团队标签 ==========
    @GetMapping("/team/{teamId}")
    public ApiResponse<List<TagResponse>> listForTeam(@AuthenticationPrincipal User user,
                                                      @PathVariable Long teamId) {
        return ApiResponse.ok(tagService.listForTeam(teamId, user.getId()));
    }

    // ========== 创建团队标签 ==========
    @PostMapping("/team")
    public ApiResponse<TagResponse> createTeam(@AuthenticationPrincipal User user,
                                               @Valid @RequestBody CreateTeamTagRequest req) {
        String color = req.getColor() != null ? req.getColor() : "#409eff";
        return ApiResponse.ok(tagService.createTeamTag(user.getId(), req.getTeamId(), req.getName(), color),
                "团队标签已创建");
    }

    // ========== 删除标签 ==========
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        tagService.delete(id, user.getId());
        return ApiResponse.ok(null, "标签已删除");
    }

    // ========== 任务-标签关联（添加） ==========
    @PostMapping("/todo/{todoId}")
    public ApiResponse<Void> addTagToTodo(@AuthenticationPrincipal User user,
                                          @PathVariable Long todoId,
                                          @Valid @RequestBody AddTagToTodoRequest req) {
        tagService.addTagToTodo(todoId, req.getTagId(), user.getId());
        return ApiResponse.ok(null, "标签已添加");
    }

    // ========== 任务-标签关联（移除） ==========
    @DeleteMapping("/todo/{todoId}/{tagId}")
    public ApiResponse<Void> removeTagFromTodo(@AuthenticationPrincipal User user,
                                               @PathVariable Long todoId,
                                               @PathVariable Long tagId) {
        tagService.removeTagFromTodo(todoId, tagId, user.getId());
        return ApiResponse.ok(null, "标签已移除");
    }

    // ========== 获取任务的所有标签 ==========
    @GetMapping("/todo/{todoId}")
    public ApiResponse<List<TagResponse>> getTagsForTodo(@AuthenticationPrincipal User user,
                                                         @PathVariable Long todoId) {
        return ApiResponse.ok(tagService.getTagsForTodo(todoId, user.getId()));
    }
}