package com.todo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.todo.dto.request.TeamRequest;
import com.todo.dto.response.ProjectResponse;
import com.todo.dto.response.TeamResponse;
import com.todo.entity.Project;
import com.todo.entity.ProjectTeam;
import com.todo.entity.Team;
import com.todo.entity.TeamMember;
import com.todo.entity.User;
import com.todo.exception.AppException;
import com.todo.mapper.ProjectMapper;
import com.todo.mapper.ProjectTeamMapper;
import com.todo.mapper.TeamMapper;
import com.todo.mapper.TeamMemberMapper;
import com.todo.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final UserMapper userMapper;
    private final ProjectTeamMapper projectTeamMapper;
    private final ProjectMapper projectMapper;

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public List<TeamResponse> myTeams(Long userId, String name) {
        List<TeamMember> memberships = teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getUserId, userId));
        if (memberships.isEmpty()) return Collections.emptyList();
        List<Long> teamIds = memberships.stream().map(TeamMember::getTeamId).collect(Collectors.toList());
        Map<Long, Team> teamMap = teamIds.isEmpty() ? Collections.emptyMap()
                : teamMapper.selectBatchIds(teamIds).stream()
                        .filter(t -> t != null)
                        .collect(Collectors.toMap(Team::getId, t -> t));
        return memberships.stream().map(m -> {
            Team team = teamMap.get(m.getTeamId());
            TeamResponse r = toResponse(team, false);
            r.setMyRole(m.getRole());
            return r;
        }).filter(r -> name == null || name.equals(r.getName()))
         .collect(Collectors.toList());
    }

    @Transactional
    public TeamResponse create(Long userId, TeamRequest req) {
        Team team = new Team();
        team.setName(req.getName());
        team.setDescription(req.getDescription());
        team.setOwnerId(userId);
        team.setInviteCode(generateCode());
        teamMapper.insert(team);

        TeamMember owner = new TeamMember();
        owner.setTeamId(team.getId());
        owner.setUserId(userId);
        owner.setRole("OWNER");
        teamMemberMapper.insert(owner);

        return toResponse(team, true);
    }

    public TeamResponse detail(Long teamId, Long userId) {
        assertMember(teamId, userId);
        TeamMember self = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId).eq(TeamMember::getUserId, userId));
        Team team = teamMapper.selectById(teamId);
        if (team == null) throw AppException.notFound("团队");
        TeamResponse r = toResponse(team, true);
        if (self != null) r.setMyRole(self.getRole());
        return r;
    }

    public TeamResponse update(Long teamId, Long userId, TeamRequest req) {
        assertAdminOrOwner(teamId, userId);
        Team team = teamMapper.selectById(teamId);
        if (team == null) throw AppException.notFound("团队");
        team.setName(req.getName());
        team.setDescription(req.getDescription());
        teamMapper.updateById(team);
        return toResponse(team, false);
    }

    @Transactional
    public void delete(Long teamId, Long userId) {
        assertOwner(teamId, userId);
        teamMemberMapper.delete(new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getTeamId, teamId));
        teamMapper.deleteById(teamId);
    }

    @Transactional
    public TeamResponse join(Long userId, String inviteCode) {
        Team team = teamMapper.selectOne(new LambdaQueryWrapper<Team>().eq(Team::getInviteCode, inviteCode));
        if (team == null) throw new AppException("邀请码无效", HttpStatus.BAD_REQUEST);

        boolean already = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, team.getId())
                .eq(TeamMember::getUserId, userId)) != null;
        if (already) throw new AppException("您已是该团队成员", HttpStatus.BAD_REQUEST);

        TeamMember member = new TeamMember();
        member.setTeamId(team.getId());
        member.setUserId(userId);
        member.setRole("MEMBER");
        teamMemberMapper.insert(member);
        return toResponse(team, false);
    }

    public void removeMember(Long teamId, Long userId, Long targetUserId) {
        assertAdminOrOwner(teamId, userId);
        TeamMember operator = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId).eq(TeamMember::getUserId, userId));
        TeamMember target = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId).eq(TeamMember::getUserId, targetUserId));
        if (target == null) throw AppException.notFound("成员");
        if ("OWNER".equals(target.getRole())) throw AppException.forbidden();
        if ("ADMIN".equals(operator.getRole()) && !"MEMBER".equals(target.getRole())) throw AppException.forbidden();
        teamMemberMapper.deleteById(target.getId());
    }

    public void updateMemberRole(Long teamId, Long operatorId, Long targetUserId, String role) {
        if (!"ADMIN".equals(role) && !"MEMBER".equals(role)) throw AppException.badRequest("无效的角色");
        if (operatorId.equals(targetUserId)) throw AppException.forbidden();
        assertOwner(teamId, operatorId);
        TeamMember target = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId).eq(TeamMember::getUserId, targetUserId));
        if (target == null) throw AppException.notFound("成员");
        if ("OWNER".equals(target.getRole())) throw AppException.forbidden();
        target.setRole(role);
        teamMemberMapper.updateById(target);
    }

    public String regenerateInviteCode(Long teamId, Long userId) {
        assertOwner(teamId, userId);
        Team team = teamMapper.selectById(teamId);
        team.setInviteCode(generateCode());
        teamMapper.updateById(team);
        return team.getInviteCode();
    }

    public List<ProjectResponse> getTeamProjects(Long teamId, Long userId) {
        // Verify user is team member
        TeamMember membership = teamMemberMapper.selectOne(
            new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId)
        );
        if (membership == null) {
            throw AppException.forbidden();
        }

        // Get projects through join table
        List<ProjectTeam> ptList = projectTeamMapper.selectList(
            new LambdaQueryWrapper<ProjectTeam>().eq(ProjectTeam::getTeamId, teamId)
        );
        if (ptList.isEmpty()) return Collections.emptyList();

        List<Long> projectIds = ptList.stream().map(ProjectTeam::getProjectId).collect(Collectors.toList());
        List<Project> projects = projectMapper.selectBatchIds(projectIds);

        // Convert to response
        return projects.stream().map(project -> {
            ProjectResponse resp = new ProjectResponse();
            resp.setId(project.getId());
            resp.setName(project.getName());
            resp.setDescription(project.getDescription());
            resp.setColor(project.getColor());
            resp.setIcon(project.getIcon());
            resp.setOwnerId(project.getOwnerId());
            resp.setIsArchived(project.getIsArchived());
            resp.setSortOrder(project.getSortOrder());
            resp.setCreateTime(project.getCreateTime());
            resp.setTeamIds(Collections.singletonList(teamId));
            return resp;
        }).collect(Collectors.toList());
    }

    private void assertMember(Long teamId, Long userId) {
        if (teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId).eq(TeamMember::getUserId, userId)) == null) {
            throw AppException.forbidden();
        }
    }

    private void assertAdminOrOwner(Long teamId, Long userId) {
        TeamMember m = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId).eq(TeamMember::getUserId, userId));
        if (m == null || "MEMBER".equals(m.getRole())) throw AppException.forbidden();
    }

    private void assertOwner(Long teamId, Long userId) {
        TeamMember m = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId).eq(TeamMember::getUserId, userId));
        if (m == null || !"OWNER".equals(m.getRole())) throw AppException.forbidden();
    }

    private TeamResponse toResponse(Team team, boolean includeMembers) {
        TeamResponse r = new TeamResponse();
        r.setId(team.getId());
        r.setName(team.getName());
        r.setDescription(team.getDescription());
        r.setInviteCode(team.getInviteCode());
        r.setOwnerId(team.getOwnerId());
        r.setCreateTime(team.getCreateTime());
        if (includeMembers) {
            List<TeamMember> members = teamMemberMapper.selectList(
                    new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getTeamId, team.getId()));
            // 批量加载用户
            java.util.Set<Long> userIds = members.stream().map(TeamMember::getUserId).collect(Collectors.toSet());
            java.util.Map<Long, User> userMap = userIds.isEmpty() ? java.util.Collections.emptyMap()
                    : userMapper.selectBatchIds(userIds).stream()
                            .filter(u -> u != null)
                            .collect(Collectors.toMap(User::getId, u -> u));
            r.setMembers(members.stream().map(m -> {
                User u = userMap.get(m.getUserId());
                TeamResponse.MemberInfo info = new TeamResponse.MemberInfo();
                info.setUserId(m.getUserId());
                info.setUsername(u != null ? u.getUsername() : "");
                info.setDisplayName(u != null ? u.getDisplayName() : "");
                info.setRole(m.getRole());
                info.setJoinedAt(m.getJoinedAt());
                return info;
            }).collect(Collectors.toList()));
        }
        return r;
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        return sb.toString();
    }
}
