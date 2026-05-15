package com.todo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.todo.dto.response.TagResponse;
import com.todo.entity.Project;
import com.todo.entity.ProjectTeam;
import com.todo.entity.Tag;
import com.todo.entity.TeamMember;
import com.todo.entity.Todo;
import com.todo.entity.TodoTag;
import com.todo.exception.AppException;
import com.todo.mapper.ProjectMapper;
import com.todo.mapper.ProjectTeamMapper;
import com.todo.mapper.TagMapper;
import com.todo.mapper.TeamMemberMapper;
import com.todo.mapper.TodoTagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagMapper tagMapper;
    private final TodoTagMapper todoTagMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final ProjectMapper projectMapper;
    private final ProjectTeamMapper projectTeamMapper;
    private final TodoService todoService;

    // ========== 查询：项目可用标签 ==========
    /**
     * 获取项目中可用的全部标签（项目标签 + 所属团队的团队标签）
     */
    public List<TagResponse> listForProject(Long projectId, Long userId) {
        Project project = assertProjectAccess(projectId, userId);

        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<>();
        // 项目标签
        wrapper.eq(Tag::getProjectId, projectId);
        // 如果项目属于某个团队，同时获取该团队的团队标签
        List<ProjectTeam> ptList = projectTeamMapper.selectList(
            new LambdaQueryWrapper<ProjectTeam>().eq(ProjectTeam::getProjectId, projectId));
        for (ProjectTeam pt : ptList) {
            wrapper.or().eq(Tag::getTeamId, pt.getTeamId());
        }
        wrapper.orderByAsc(Tag::getCreateTime);
        List<Tag> tags = tagMapper.selectList(wrapper);
        return tags.stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ========== 查询：团队标签 ==========
    /**
     * 获取团队标签（仅团队标签，不含项目标签）
     */
    public List<TagResponse> listForTeam(Long teamId, Long userId) {
        // 简单校验是否为团队成员
        if (!isTeamMember(teamId, userId)) {
            throw AppException.forbidden();
        }
        List<Tag> tags = tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getTeamId, teamId)
                .orderByAsc(Tag::getCreateTime));
        return tags.stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ========== 创建项目标签 ==========
    @Transactional
    public TagResponse createProjectTag(Long userId, Long projectId, String name, String color) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) throw AppException.notFound("项目");

        // 权限检查：个人项目所有者 或 团队项目 ADMIN/OWNER
        assertCanManageProjectTags(project, userId);

        // 同一项目下标签名称唯一
        Tag exist = tagMapper.selectOne(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getProjectId, projectId)
                .eq(Tag::getName, name));
        if (exist != null) throw AppException.badRequest("项目标签名称已存在");

        Tag tag = new Tag();
        tag.setName(name);
        tag.setColor(color != null ? color : "#409eff");
        tag.setProjectId(projectId);
        tag.setOwnerId(userId);   // 记录创建者，但不作为归属依据
        tag.setTeamId(null);
        tagMapper.insert(tag);

        return toResponse(tag);
    }

    // ========== 创建团队标签 ==========
    @Transactional
    public TagResponse createTeamTag(Long userId, Long teamId, String name, String color) {
        // 必须是团队 ADMIN 或 OWNER
        TeamMember member = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId));
        if (member == null || "MEMBER".equals(member.getRole())) {
            throw AppException.forbidden();
        }

        // 同一团队下标签名称唯一
        Tag exist = tagMapper.selectOne(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getTeamId, teamId)
                .eq(Tag::getName, name));
        if (exist != null) throw AppException.badRequest("团队标签名称已存在");

        Tag tag = new Tag();
        tag.setName(name);
        tag.setColor(color != null ? color : "#409eff");
        tag.setTeamId(teamId);
        tag.setOwnerId(null);
        tag.setProjectId(null);
        tagMapper.insert(tag);

        return toResponse(tag);
    }

    // ========== 删除标签 ==========
    @Transactional
    public void delete(Long tagId, Long userId) {
        Tag tag = tagMapper.selectById(tagId);
        if (tag == null) throw AppException.notFound("标签");

        if (tag.getProjectId() != null) {
            // 删除项目标签：需要项目管理员权限
            Project project = projectMapper.selectById(tag.getProjectId());
            if (project == null) throw AppException.notFound("项目");
            assertCanManageProjectTags(project, userId);
        } else if (tag.getTeamId() != null) {
            // 删除团队标签：需要团队 ADMIN/OWNER
            TeamMember member = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                    .eq(TeamMember::getTeamId, tag.getTeamId())
                    .eq(TeamMember::getUserId, userId));
            if (member == null || "MEMBER".equals(member.getRole())) {
                throw AppException.forbidden();
            }
        } else {
            // 既无项目归属也无团队归属的标签不允许删除（系统标签概念，实际上不会有）
            throw AppException.badRequest("无法删除系统标签");
        }

        // 删除标签与待办的关联
        todoTagMapper.delete(new LambdaQueryWrapper<TodoTag>().eq(TodoTag::getTagId, tagId));
        // 删除标签本身
        tagMapper.deleteById(tagId);
    }

    // ========== 为待办添加标签 ==========
    @Transactional
    public void addTagToTodo(Long todoId, Long tagId, Long userId) {
        Todo todo = todoService.getAndAssertAccess(todoId, userId, false);
        if (todo.getProjectId() == null) {
            throw AppException.badRequest("待办必须属于一个项目才能使用标签");
        }
        Tag tag = tagMapper.selectById(tagId);
        if (tag == null) throw AppException.notFound("标签");

        Project project = projectMapper.selectById(todo.getProjectId());
        if (project == null) throw AppException.notFound("项目");

        // 合法性检查：标签必须属于该项目或该项目所属团队
        boolean valid = false;
        if (tag.getProjectId() != null && tag.getProjectId().equals(project.getId())) {
            valid = true;
        } else if (tag.getTeamId() != null) {
            List<ProjectTeam> ptList = projectTeamMapper.selectList(
                new LambdaQueryWrapper<ProjectTeam>().eq(ProjectTeam::getProjectId, project.getId()));
            valid = ptList.stream().anyMatch(pt -> pt.getTeamId().equals(tag.getTeamId()));
        }
        if (!valid) {
            throw AppException.badRequest("该标签不属于当前项目或所属团队");
        }

        // 避免重复添加
        TodoTag exist = todoTagMapper.selectOne(new LambdaQueryWrapper<TodoTag>()
                .eq(TodoTag::getTodoId, todoId)
                .eq(TodoTag::getTagId, tagId));
        if (exist == null) {
            TodoTag todoTag = new TodoTag();
            todoTag.setTodoId(todoId);
            todoTag.setTagId(tagId);
            todoTagMapper.insert(todoTag);
        }
    }

    // ========== 从待办移除标签 ==========
    @Transactional
    public void removeTagFromTodo(Long todoId, Long tagId, Long userId) {
        // 校验待办访问权限
        todoService.getAndAssertAccess(todoId, userId, false);
        // 直接删除关联（无需校验标签权限，移除标签不影响标签本身）
        todoTagMapper.delete(new LambdaQueryWrapper<TodoTag>()
                .eq(TodoTag::getTodoId, todoId)
                .eq(TodoTag::getTagId, tagId));
    }

    // ========== 获取某个待办的标签列表 ==========
    public List<TagResponse> getTagsForTodo(Long todoId, Long userId) {
        todoService.getAndAssertAccess(todoId, userId, false);
        List<TodoTag> todoTags = todoTagMapper.selectList(
                new LambdaQueryWrapper<TodoTag>().eq(TodoTag::getTodoId, todoId));
        if (todoTags.isEmpty()) return Collections.emptyList();
        List<Long> tagIds = todoTags.stream().map(TodoTag::getTagId).collect(Collectors.toList());
        List<Tag> tags = tagMapper.selectBatchIds(tagIds);
        java.util.Map<Long, Tag> tagMap = tags.stream()
                .filter(t -> t != null)
                .collect(Collectors.toMap(Tag::getId, t -> t));
        return tagIds.stream()
                .map(tagMap::get)
                .filter(t -> t != null)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /** 批量获取多个待办的标签，返回 Map<todoId, List<TagResponse>> */
    public java.util.Map<Long, List<TagResponse>> getTagsForTodos(List<Long> todoIds, Long userId) {
        if (todoIds.isEmpty()) return Collections.emptyMap();
        List<TodoTag> todoTags = todoTagMapper.selectList(
                new LambdaQueryWrapper<TodoTag>().in(TodoTag::getTodoId, todoIds));
        if (todoTags.isEmpty()) return Collections.emptyMap();
        java.util.Set<Long> tagIds = todoTags.stream().map(TodoTag::getTagId).collect(Collectors.toSet());
        java.util.Map<Long, Tag> tagMap = tagMapper.selectBatchIds(tagIds).stream()
                .filter(t -> t != null)
                .collect(Collectors.toMap(Tag::getId, t -> t));
        java.util.Map<Long, List<TagResponse>> result = new java.util.HashMap<>();
        for (TodoTag tt : todoTags) {
            Tag tag = tagMap.get(tt.getTagId());
            if (tag != null) {
                result.computeIfAbsent(tt.getTodoId(), k -> new java.util.ArrayList<>()).add(toResponse(tag));
            }
        }
        return result;
    }

    // ========== 内部校验 ==========
    private Project assertProjectAccess(Long projectId, Long userId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) throw AppException.notFound("项目");

        List<ProjectTeam> ptList = projectTeamMapper.selectList(
            new LambdaQueryWrapper<ProjectTeam>().eq(ProjectTeam::getProjectId, project.getId()));
        if (ptList.isEmpty()) {
            if (!project.getOwnerId().equals(userId)) throw AppException.forbidden();
        } else {
            boolean isMember = false;
            for (ProjectTeam pt : ptList) {
                if (isTeamMember(pt.getTeamId(), userId)) { isMember = true; break; }
            }
            if (!isMember) throw AppException.forbidden();
        }
        return project;
    }

    private void assertCanManageProjectTags(Project project, Long userId) {
        List<ProjectTeam> ptList = projectTeamMapper.selectList(
            new LambdaQueryWrapper<ProjectTeam>().eq(ProjectTeam::getProjectId, project.getId()));
        if (!ptList.isEmpty()) {
            boolean isAdmin = false;
            for (ProjectTeam pt : ptList) {
                TeamMember member = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, pt.getTeamId())
                        .eq(TeamMember::getUserId, userId));
                if (member != null && !"MEMBER".equals(member.getRole())) { isAdmin = true; break; }
            }
            if (!isAdmin) throw AppException.forbidden();
        } else {
            if (!project.getOwnerId().equals(userId)) {
                throw AppException.forbidden();
            }
        }
    }

    private boolean isTeamMember(Long teamId, Long userId) {
        return teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId)) != null;
    }

    private TagResponse toResponse(Tag tag) {
        TagResponse r = new TagResponse();
        r.setId(tag.getId());
        r.setName(tag.getName());
        r.setColor(tag.getColor());
        r.setOwnerId(tag.getOwnerId());
        r.setTeamId(tag.getTeamId());
        // 需要在 TagResponse 中添加 projectId 字段，这里假设已存在
        r.setProjectId(tag.getProjectId());
        r.setCreateTime(tag.getCreateTime());
        return r;
    }
}