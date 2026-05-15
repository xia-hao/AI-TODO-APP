package com.todo.controller;

import com.todo.dto.request.*;
import com.todo.dto.response.ApiResponse;
import com.todo.dto.response.ProjectResponse;
import com.todo.dto.response.TeamResponse;
import com.todo.entity.User;
import com.todo.service.TeamService;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
@Validated
public class TeamController {

    private final TeamService teamService;

    @GetMapping
    public ApiResponse<List<TeamResponse>> myTeams(@AuthenticationPrincipal User user,
                                                    @RequestParam(required = false) String name) {
        return ApiResponse.ok(teamService.myTeams(user.getId(), name));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TeamResponse> create(@AuthenticationPrincipal User user,
                                             @Valid @RequestBody TeamRequest req) {
        return ApiResponse.ok(teamService.create(user.getId(), req), "团队创建成功");
    }

    @GetMapping("/{id}")
    public ApiResponse<TeamResponse> detail(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return ApiResponse.ok(teamService.detail(id, user.getId()));
    }

    @PutMapping("/{id}")
    public ApiResponse<TeamResponse> update(@AuthenticationPrincipal User user,
                                             @PathVariable Long id,
                                             @Valid @RequestBody TeamRequest req) {
        return ApiResponse.ok(teamService.update(id, user.getId(), req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        teamService.delete(id, user.getId());
        return ApiResponse.ok(null, "团队已解散");
    }

    @PostMapping("/join")
    public ApiResponse<TeamResponse> join(@AuthenticationPrincipal User user,
                                           @Valid @RequestBody JoinTeamRequest req) {
        return ApiResponse.ok(teamService.join(user.getId(), req.getInviteCode()), "加入成功");
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ApiResponse<Void> removeMember(@AuthenticationPrincipal User user,
                                           @PathVariable Long id,
                                           @PathVariable Long userId) {
        teamService.removeMember(id, user.getId(), userId);
        return ApiResponse.ok(null, "成员已移除");
    }

    @PutMapping("/{id}/members/{userId}/role")
    public ApiResponse<Void> updateMemberRole(@AuthenticationPrincipal User user,
                                               @PathVariable Long id,
                                               @PathVariable Long userId,
                                               @Valid @RequestBody UpdateMemberRoleRequest req) {
        teamService.updateMemberRole(id, user.getId(), userId, req.getRole());
        return ApiResponse.ok(null, "角色已更新");
    }

    @GetMapping("/{id}/invite-code")
    public ApiResponse<Map<String, String>> regenerateCode(@AuthenticationPrincipal User user,
                                                            @PathVariable Long id) {
        String code = teamService.regenerateInviteCode(id, user.getId());
        return ApiResponse.ok(Collections.singletonMap("inviteCode", code));
    }

    /**
     * 获取团队参与的项目列表
     */
    @GetMapping("/{teamId}/projects")
    public ApiResponse<List<ProjectResponse>> getTeamProjects(@AuthenticationPrincipal User user,
                                                              @PathVariable Long teamId) {
        return ApiResponse.ok(teamService.getTeamProjects(teamId, user.getId()));
    }
}
