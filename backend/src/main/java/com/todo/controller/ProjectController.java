package com.todo.controller;

import com.todo.dto.request.ProjectRequest;
import com.todo.dto.response.ApiResponse;
import com.todo.dto.response.ProjectResponse;
import com.todo.entity.User;
import com.todo.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@Validated
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public ApiResponse<List<ProjectResponse>> list(@AuthenticationPrincipal User user,
                                                    @RequestParam(required = false) String name) {
        return ApiResponse.ok(projectService.listAccessible(user.getId(), name));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProjectResponse> detail(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return ApiResponse.ok(projectService.getDetail(id, user.getId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProjectResponse> create(@AuthenticationPrincipal User user,
                                               @Valid @RequestBody ProjectRequest req) {
        return ApiResponse.ok(projectService.create(user.getId(), req), "项目创建成功");
    }

    @PutMapping("/{id}")
    public ApiResponse<ProjectResponse> update(@AuthenticationPrincipal User user,
                                               @PathVariable Long id,
                                               @Valid @RequestBody ProjectRequest req) {
        return ApiResponse.ok(projectService.update(id, user.getId(), req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        projectService.delete(id, user.getId());
        return ApiResponse.ok(null, "项目已删除");
    }

    @GetMapping("/by-team")
    public ApiResponse<List<ProjectResponse>> listByTeam(@AuthenticationPrincipal User user,
                                                         @RequestParam @NotNull(message = "团队ID不能为空") Long teamId) {
        return ApiResponse.ok(projectService.listByTeam(teamId, user.getId()));
    }

    /**
     * 获取项目关联的团队列表
     */
    @GetMapping("/{projectId}/teams")
    public ApiResponse<List<ProjectResponse.TeamBrief>> getProjectTeams(@AuthenticationPrincipal User user,
                                                                          @PathVariable Long projectId) {
        return ApiResponse.ok(projectService.getProjectTeams(projectId, user.getId()));
    }

    /**
     * 关联团队到项目
     */
    @PostMapping("/{projectId}/teams/{teamId}")
    public ApiResponse<Void> addTeam(@AuthenticationPrincipal User user,
                                      @PathVariable Long projectId,
                                      @PathVariable Long teamId) {
        projectService.addTeam(projectId, teamId, user.getId());
        return ApiResponse.ok(null, "团队关联成功");
    }

    /**
     * 取消关联团队
     */
    @DeleteMapping("/{projectId}/teams/{teamId}")
    public ApiResponse<Void> removeTeam(@AuthenticationPrincipal User user,
                                         @PathVariable Long projectId,
                                         @PathVariable Long teamId) {
        projectService.removeTeam(projectId, teamId, user.getId());
        return ApiResponse.ok(null, "团队已取消关联");
    }

}