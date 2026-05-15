package com.todo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.todo.dto.request.ProjectRequest;
import com.todo.dto.response.ProjectResponse;
import com.todo.dto.response.SectionResponse;
import com.todo.entity.Project;
import com.todo.entity.ProjectTeam;
import com.todo.entity.Section;
import com.todo.entity.Team;
import com.todo.entity.TeamMember;
import com.todo.exception.AppException;
import com.todo.mapper.ProjectMapper;
import com.todo.mapper.ProjectTeamMapper;
import com.todo.mapper.SectionMapper;
import com.todo.mapper.TeamMapper;
import com.todo.mapper.TeamMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final SectionMapper sectionMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final ProjectTeamMapper projectTeamMapper;
    private final TeamMapper teamMapper;

    /**
     * 获取用户可访问的所有项目（个人项目 + 所在团队的项目）
     */
    public List<ProjectResponse> listAccessible(Long userId, String name) {
        // User's own projects (ownerId = userId)
        List<Project> ownedProjects = projectMapper.selectList(
                new LambdaQueryWrapper<Project>().eq(Project::getOwnerId, userId)
        );

        // Projects from teams the user belongs to
        List<TeamMember> memberships = teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getUserId, userId)
        );

        if (memberships.isEmpty()) {
            return ownedProjects.stream().map(p -> toResponse(p))
                    .filter(r -> name == null || name.equals(r.getName()))
                    .collect(Collectors.toList());
        }

        List<Long> teamIds = memberships.stream().map(TeamMember::getTeamId).collect(Collectors.toList());
        List<ProjectTeam> ptList = projectTeamMapper.selectList(
                new LambdaQueryWrapper<ProjectTeam>().in(ProjectTeam::getTeamId, teamIds)
        );

        Set<Long> projectIds = ownedProjects.stream().map(Project::getId).collect(Collectors.toSet());
        ptList.stream().map(ProjectTeam::getProjectId).forEach(projectIds::add);

        if (projectIds.isEmpty()) return Collections.emptyList();
        List<Project> projects = projectMapper.selectBatchIds(projectIds);
        return projects.stream().map(p -> toResponse(p))
                .filter(r -> name == null || name.equals(r.getName()))
                .collect(Collectors.toList());
    }

    /**
     * 获取单个项目详情（含权限校验和 sections）
     */
    public ProjectResponse getDetail(Long projectId, Long userId) {
        Project project = assertAccess(projectId, userId);
        ProjectResponse resp = toResponse(project);
        List<Section> sections = sectionMapper.selectList(
                new LambdaQueryWrapper<Section>()
                        .eq(Section::getProjectId, projectId)
                        .orderByAsc(Section::getSortOrder));
        resp.setSections(sections.stream().map(this::toSectionResponse).collect(Collectors.toList()));
        return resp;
    }

    @Transactional
    public ProjectResponse create(Long userId, ProjectRequest req) {
        validateTeamRoles(userId, req.getTeamIds());

        Project project = new Project();
        project.setName(req.getName());
        project.setDescription(req.getDescription());
        project.setColor(req.getColor() != null ? req.getColor() : "#409eff");
        project.setIcon(req.getIcon() != null ? req.getIcon() : "folder");
        project.setOwnerId(userId);
        project.setIsArchived(false);
        project.setSortOrder(0);
        projectMapper.insert(project);

        // Insert project-team associations (batch)
        if (req.getTeamIds() != null && !req.getTeamIds().isEmpty()) {
            for (Long teamId : req.getTeamIds()) {
                ProjectTeam pt = new ProjectTeam();
                pt.setProjectId(project.getId());
                pt.setTeamId(teamId);
                projectTeamMapper.insert(pt);
            }
        }

        // 创建默认三个分区
        String[] defaultSections = {"待处理", "进行中", "已完成"};
        for (int i = 0; i < defaultSections.length; i++) {
            Section section = new Section();
            section.setProjectId(project.getId());
            section.setName(defaultSections[i]);
            section.setSortOrder(i);
            sectionMapper.insert(section);
        }

        return getDetail(project.getId(), userId);
    }

    @Transactional
    public ProjectResponse update(Long projectId, Long userId, ProjectRequest req) {
        Project project = assertOwnerOrAdmin(projectId, userId);
        validateTeamRoles(userId, req.getTeamIds());
        project.setName(req.getName());
        project.setDescription(req.getDescription());
        if (req.getColor() != null) project.setColor(req.getColor());
        if (req.getIcon() != null) project.setIcon(req.getIcon());
        projectMapper.updateById(project);

        // Sync project-team associations
        projectTeamMapper.delete(
                new LambdaQueryWrapper<ProjectTeam>().eq(ProjectTeam::getProjectId, projectId)
        );
        if (req.getTeamIds() != null && !req.getTeamIds().isEmpty()) {
            for (Long teamId : req.getTeamIds()) {
                ProjectTeam pt = new ProjectTeam();
                pt.setProjectId(projectId);
                pt.setTeamId(teamId);
                projectTeamMapper.insert(pt);
            }
        }

        return getDetail(projectId, userId);
    }

    @Transactional
    public void delete(Long projectId, Long userId) {
        assertOwner(projectId, userId);
        // 级联删除 sections（已有外键 ON DELETE CASCADE）
        projectMapper.deleteById(projectId);
    }

    public List<ProjectResponse> listByTeam(Long teamId, Long userId) {
        // 校验用户是否属于该团队
        if (!isTeamMember(teamId, userId)) {
            throw AppException.forbidden();
        }
        List<ProjectTeam> ptList = projectTeamMapper.selectList(
                new LambdaQueryWrapper<ProjectTeam>().eq(ProjectTeam::getTeamId, teamId)
        );
        if (ptList.isEmpty()) return Collections.emptyList();

        List<Long> projectIds = ptList.stream().map(ProjectTeam::getProjectId).collect(Collectors.toList());
        List<Project> projects = projectMapper.selectBatchIds(projectIds);
        return projects.stream().map(p -> toResponse(p)).collect(Collectors.toList());
    }

    /**
     * 获取项目关联的团队列表
     */
    public List<ProjectResponse.TeamBrief> getProjectTeams(Long projectId, Long userId) {
        assertAccess(projectId, userId);

        List<ProjectTeam> ptList = projectTeamMapper.selectList(
            new LambdaQueryWrapper<ProjectTeam>().eq(ProjectTeam::getProjectId, projectId)
        );
        if (ptList.isEmpty()) return Collections.emptyList();

        List<Team> teams = teamMapper.selectBatchIds(
            ptList.stream().map(ProjectTeam::getTeamId).collect(Collectors.toList())
        );
        return teams.stream().map(t -> {
            ProjectResponse.TeamBrief brief = new ProjectResponse.TeamBrief();
            brief.setId(t.getId());
            brief.setName(t.getName());
            return brief;
        }).collect(Collectors.toList());
    }

    /**
     * 关联团队到项目
     */
    @Transactional
    public void addTeam(Long projectId, Long teamId, Long userId) {
        assertAccess(projectId, userId);

        // Check if already associated
        Long count = projectTeamMapper.selectCount(
            new LambdaQueryWrapper<ProjectTeam>()
                .eq(ProjectTeam::getProjectId, projectId)
                .eq(ProjectTeam::getTeamId, teamId)
        );
        if (count > 0) throw new IllegalArgumentException("该团队已关联");

        ProjectTeam pt = new ProjectTeam();
        pt.setProjectId(projectId);
        pt.setTeamId(teamId);
        projectTeamMapper.insert(pt);
    }

    /**
     * 取消关联团队
     */
    @Transactional
    public void removeTeam(Long projectId, Long teamId, Long userId) {
        assertAccess(projectId, userId);

        projectTeamMapper.delete(
            new LambdaQueryWrapper<ProjectTeam>()
                .eq(ProjectTeam::getProjectId, projectId)
                .eq(ProjectTeam::getTeamId, teamId)
        );
    }

    // ---------- 权限检查辅助方法 ----------

    private Project assertAccess(Long projectId, Long userId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) throw AppException.notFound("项目");

        // Owner always has access
        if (project.getOwnerId().equals(userId)) return project;

        // Check if user is a member of any associated team
        List<ProjectTeam> ptList = projectTeamMapper.selectList(
                new LambdaQueryWrapper<ProjectTeam>().eq(ProjectTeam::getProjectId, projectId)
        );
        if (ptList.isEmpty()) throw AppException.forbidden();

        for (ProjectTeam pt : ptList) {
            if (isTeamMember(pt.getTeamId(), userId)) return project;
        }
        throw AppException.forbidden();
    }

    private Project assertOwnerOrAdmin(Long projectId, Long userId) {
        Project project = assertAccess(projectId, userId);
        if (project.getOwnerId().equals(userId)) return project;

        List<ProjectTeam> ptList = projectTeamMapper.selectList(
                new LambdaQueryWrapper<ProjectTeam>().eq(ProjectTeam::getProjectId, projectId)
        );
        for (ProjectTeam pt : ptList) {
            TeamMember member = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                    .eq(TeamMember::getTeamId, pt.getTeamId())
                    .eq(TeamMember::getUserId, userId));
            if (member != null && !"MEMBER".equals(member.getRole())) return project;
        }
        throw AppException.forbidden();
    }

    private void assertOwner(Long projectId, Long userId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) throw AppException.notFound("项目");
        if (!project.getOwnerId().equals(userId)) throw AppException.forbidden();
    }

    private boolean isTeamMember(Long teamId, Long userId) {
        return teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId)) != null;
    }

    private void validateTeamRoles(Long userId, List<Long> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) return;
        for (Long teamId : teamIds) {
            TeamMember member = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                    .eq(TeamMember::getTeamId, teamId)
                    .eq(TeamMember::getUserId, userId));
            if (member == null || "MEMBER".equals(member.getRole())) {
                throw AppException.forbidden();
            }
        }
    }

    private ProjectResponse toResponse(Project project) {
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

        // Load team associations
        List<ProjectTeam> ptList = projectTeamMapper.selectList(
                new LambdaQueryWrapper<ProjectTeam>().eq(ProjectTeam::getProjectId, project.getId())
        );
        List<Long> teamIds = ptList.stream().map(ProjectTeam::getTeamId).collect(Collectors.toList());
        resp.setTeamIds(teamIds);

        if (!teamIds.isEmpty()) {
            List<Team> teams = teamMapper.selectBatchIds(teamIds);
            resp.setTeams(teams.stream().map(t -> {
                ProjectResponse.TeamBrief brief = new ProjectResponse.TeamBrief();
                brief.setId(t.getId());
                brief.setName(t.getName());
                return brief;
            }).collect(Collectors.toList()));
        } else {
            resp.setTeams(Collections.emptyList());
        }

        return resp;
    }

    private SectionResponse toSectionResponse(Section section) {
        SectionResponse resp = new SectionResponse();
        resp.setId(section.getId());
        resp.setProjectId(section.getProjectId());
        resp.setName(section.getName());
        resp.setSortOrder(section.getSortOrder());
        resp.setCreateTime(section.getCreateTime());
        return resp;
    }
}
