package com.todo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.todo.dto.request.*;
import com.todo.dto.response.TagResponse;
import com.todo.dto.response.TodoResponse;
import com.todo.dto.response.TodoSearchResult;
import com.todo.entity.Project;
import com.todo.entity.ProjectTeam;
import com.todo.entity.Section;
import com.todo.entity.Team;
import com.todo.entity.TeamMember;
import com.todo.entity.Todo;
import com.todo.entity.User;
import com.todo.exception.AppException;
import com.todo.mapper.ProjectMapper;
import com.todo.mapper.ProjectTeamMapper;
import com.todo.mapper.SectionMapper;
import com.todo.mapper.TeamMapper;
import com.todo.mapper.TeamMemberMapper;
import com.todo.mapper.TodoMapper;
import com.todo.mapper.UserMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TodoService {

    private final TodoMapper todoMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final UserMapper userMapper;
    private final ProjectMapper projectMapper;
    private final ProjectTeamMapper projectTeamMapper;
    private final SectionMapper sectionMapper;
    private final TeamMapper teamMapper;
    private final TagService tagService;
    private final ActivityLogService activityLogService;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;

    // 显式构造器，TagService 延迟注入
    public TodoService(TodoMapper todoMapper,
                       TeamMemberMapper teamMemberMapper,
                       UserMapper userMapper,
                       ProjectMapper projectMapper,
                       ProjectTeamMapper projectTeamMapper,
                       SectionMapper sectionMapper,
                       TeamMapper teamMapper,
                       @Lazy TagService tagService,
                       ActivityLogService activityLogService,
                       SimpMessagingTemplate messagingTemplate,
                       @Lazy NotificationService notificationService) {
        this.todoMapper = todoMapper;
        this.teamMemberMapper = teamMemberMapper;
        this.userMapper = userMapper;
        this.projectMapper = projectMapper;
        this.projectTeamMapper = projectTeamMapper;
        this.sectionMapper = sectionMapper;
        this.teamMapper = teamMapper;
        this.tagService = tagService;
        this.activityLogService = activityLogService;
        this.messagingTemplate = messagingTemplate;
        this.notificationService = notificationService;
    }

    /** 向负责人推送任务分配通知 */
    private void notifyAssignee(Long assigneeId, Long projectId, Long todoId, String text) {
        if (assigneeId == null || projectId == null) return;
        notificationService.create(
            assigneeId,
            "TODO_ASSIGNED",
            "任务分配通知",
            "你被分配了任务: " + text,
            "/projects/" + projectId + "?todo=" + todoId);
    }

    /** 向项目看板广播变更，通知前端刷新 */
    private void broadcastProjectUpdate(Long projectId) {
        if (projectId == null) return;
        java.util.Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "TODO_UPDATED");
        msg.put("projectId", projectId);
        messagingTemplate.convertAndSend("/topic/projects/" + projectId, msg);
    }

    public List<TodoResponse> list(TodoListQuery query) {
        Long userId = query.getUserId();
        Long teamId = query.getTeamId();
        String status = query.getStatus();
        String category = query.getCategory();
        String q = query.getQ();

        LambdaQueryWrapper<Todo> wrapper = new LambdaQueryWrapper<Todo>()
                .eq(StringUtils.hasText(category), Todo::getCategory, category)
                .like(StringUtils.hasText(q), Todo::getText, q)
                .orderByAsc(Todo::getSortOrder);

        if (teamId != null) {
            assertTeamMember(teamId, userId);
            List<ProjectTeam> ptList = projectTeamMapper.selectList(
                    new LambdaQueryWrapper<ProjectTeam>().eq(ProjectTeam::getTeamId, teamId));
            List<Long> projectIds = ptList.stream().map(ProjectTeam::getProjectId).collect(Collectors.toList());
            if (projectIds.isEmpty()) return Collections.emptyList();
            wrapper.eq(Todo::getTeamId, teamId)
                   .in(Todo::getProjectId, projectIds);
        } else {
            List<Long> myProjectIds = projectMapper.selectList(
                    new LambdaQueryWrapper<Project>()
                            .eq(Project::getOwnerId, userId))
                    .stream().map(Project::getId).collect(Collectors.toList());
            wrapper.eq(Todo::getOwnerId, userId)
                   .and(w -> {
                       if (!myProjectIds.isEmpty()) {
                           w.in(Todo::getProjectId, myProjectIds).or().isNull(Todo::getProjectId);
                       } else {
                           w.isNull(Todo::getProjectId);
                       }
                   });
        }

        if ("active".equals(status)) wrapper.eq(Todo::getCompleted, false);
        else if ("completed".equals(status)) wrapper.eq(Todo::getCompleted, true);

        List<Todo> todos = todoMapper.selectList(wrapper);
        java.util.Map<Long, List<TagResponse>> tagMap = batchLoadTags(todos);
        java.util.Map<Long, User> userMap = batchLoadUsers(todos);
        java.util.Map<Long, Project> projectMap = batchLoadProjects(todos);
        java.util.Map<Long, Team> teamMap = batchLoadTeams(todos);
        java.util.Map<Long, Section> sectionMap = batchLoadSections(todos);
        return todos.stream().map(item -> toResponse(item, userMap, projectMap, teamMap, sectionMap, tagMap)).collect(Collectors.toList());
    }

    // ========== 新增：按项目查询 ==========
    public List<TodoResponse> listByProject(TodoProjectQuery query) {
        Long userId = query.getUserId();
        Long projectId = query.getProjectId();
        Long sectionId = query.getSectionId();
        String status = query.getStatus();
        String category = query.getCategory();
        String q = query.getQ();
        List<Long> tagIds = query.getTagIds();
        String dateFrom = query.getDateFrom();
        String dateTo = query.getDateTo();
        // ... 原有权限校验

        LambdaQueryWrapper<Todo> wrapper = new LambdaQueryWrapper<Todo>()
                .eq(Todo::getProjectId, projectId)
                .isNull(Todo::getDeletedTime)
                .eq(sectionId != null, Todo::getSectionId, sectionId)
                .eq(StringUtils.hasText(category), Todo::getCategory, category)
                .like(StringUtils.hasText(q), Todo::getText, q)
                .ge(StringUtils.hasText(dateFrom), Todo::getDueDate, dateFrom)    // 新增
                .le(StringUtils.hasText(dateTo), Todo::getDueDate, dateTo)        // 新增
                .orderByAsc(Todo::getSortOrder);

        if ("active".equals(status)) wrapper.eq(Todo::getCompleted, false);
        else if ("completed".equals(status)) wrapper.eq(Todo::getCompleted, true);

        // 标签筛选：通过 todo_tags 子查询
        if (tagIds != null && !tagIds.isEmpty()) {
            wrapper.inSql(Todo::getId,
                    "SELECT todo_id FROM todo_tags WHERE tag_id IN (" +
                            tagIds.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")");
        }

        List<Todo> todos = todoMapper.selectList(wrapper);
        java.util.Map<Long, List<TagResponse>> tagMap = batchLoadTags(todos);
        java.util.Map<Long, User> userMap = batchLoadUsers(todos);
        java.util.Map<Long, Project> projectMap = batchLoadProjects(todos);
        java.util.Map<Long, Team> teamMap = batchLoadTeams(todos);
        java.util.Map<Long, Section> sectionMap = batchLoadSections(todos);
        return todos.stream().map(item -> toResponse(item, userMap, projectMap, teamMap, sectionMap, tagMap)).collect(Collectors.toList());
    }

    public TodoResponse create(Long userId, TodoRequest req) {
        if (req.getProjectId() == null) {
            throw AppException.badRequest("所属项目不能为空");
        }
        if(req.getTeamId() == null && req.getAssigneeId() != null ){
            throw AppException.badRequest("处理人团队不能为空！");
        }
        assertProjectAccess(req.getProjectId(), userId);
        Todo todo = new Todo();
        todo.setOwnerId(userId);
        todo.setTeamId(req.getTeamId());
        todo.setProjectId(req.getProjectId());
        todo.setSectionId(req.getSectionId() != null ? req.getSectionId()
            : resolveDefaultSectionId(req.getProjectId()));
        if (todo.getSectionId() == null) {
            throw AppException.badRequest("所属分区不能为空，请先创建分区");
        }
        todo.setText(req.getText());
        todo.setCategory(req.getCategory());
        todo.setPriority(req.getPriority());
        todo.setDueDate(req.getDueDate());
        todo.setCompleted(false);
        if (req.getTeamId() != null) {
            applyAssignee(todo, req.getTeamId(), userId, req.getAssigneeId());
        }
        todo.setSortOrder(nextSortOrder(userId, req.getTeamId(), req.getProjectId()));
        todoMapper.insert(todo);
        Long projectId = resolveProjectId(todo);
        if (projectId != null) {
            activityLogService.log(projectId, userId, "create", "Todo", todo.getId(), "创建待办: " + todo.getText());
            broadcastProjectUpdate(projectId);
        }
        if (todo.getAssigneeId() != null) {
            notifyAssignee(todo.getAssigneeId(), projectId, todo.getId(), todo.getText());
        }
        return toResponse(todo,userId);
    }

    @Transactional
    public List<TodoResponse> batchImport(Long projectId, Long userId, List<ImportItem> items) {
        assertProjectAccess(projectId, userId);
        Project project = projectMapper.selectById(projectId);
        // Get first team from join table for default team context
        Long projectTeamId = null;
        List<ProjectTeam> ptList = projectTeamMapper.selectList(
            new LambdaQueryWrapper<ProjectTeam>().eq(ProjectTeam::getProjectId, projectId));
        if (!ptList.isEmpty()) projectTeamId = ptList.get(0).getTeamId();
        List<Todo> batch = new ArrayList<>();
        int sortOrder = nextSortOrder(userId, projectTeamId, projectId);
        for (ImportItem item : items) {
            if (!StringUtils.hasText(item.getText())) continue;
            Todo todo = new Todo();
            todo.setOwnerId(userId);
            todo.setProjectId(projectId);
            todo.setTeamId(projectTeamId);
            todo.setText(item.getText());
            todo.setCategory(item.getCategory() != null ? item.getCategory() : "其他");
            todo.setPriority(item.getPriority() != null ? item.getPriority() : "medium");
            todo.setDueDate(item.getDueDate() != null ? LocalDate.parse(item.getDueDate()) : null);
            todo.setCompleted(item.getCompleted() != null ? item.getCompleted() : false);
            todo.setSortOrder(sortOrder++);
            batch.add(todo);
        }
        if (!batch.isEmpty()) {
            batch.forEach(todoMapper::insert);
        }
        List<TodoResponse> results = batch.stream().map(todo -> toResponse(todo, userId)).collect(Collectors.toList());
        if (!results.isEmpty()) {
            activityLogService.log(projectId, userId, "import", "Todo", projectId, "批量导入 " + results.size() + " 条待办");
            broadcastProjectUpdate(projectId);
        }
        return results;
    }

    public TodoResponse update(Long todoId, Long userId, TodoRequest req) {
        if(req.getTeamId() == null && req.getAssigneeId() != null ){
            throw AppException.badRequest("处理人团队不能为空！");
        }
        Todo todo = getAndAssertAccess(todoId, userId, false);
        Long oldAssigneeId = todo.getAssigneeId();
        todo.setText(req.getText());
        todo.setCategory(req.getCategory());
        todo.setPriority(req.getPriority());
        todo.setDueDate(req.getDueDate());
        // 支持变更团队
        Long effectiveTeamId = req.getTeamId() != null ? req.getTeamId() : todo.getTeamId();
        if (req.getTeamId() != null) {
            todo.setTeamId(req.getTeamId());
        }
        // 支持变更项目
        if (req.getProjectId() != null) {
            todo.setProjectId(req.getProjectId());
        }
        // 支持变更分区
        if (req.getSectionId() != null) {
            todo.setSectionId(req.getSectionId());
        }
        // 分配人：团队存在时设置，否则清空
        Long newAssigneeId = null;
        if (effectiveTeamId != null) {
            applyAssignee(todo, effectiveTeamId, userId, req.getAssigneeId());
            newAssigneeId = todo.getAssigneeId();
        }
        todoMapper.update(null, new LambdaUpdateWrapper<Todo>()
                .set(Todo::getText, todo.getText())
                .set(Todo::getCategory, todo.getCategory())
                .set(Todo::getPriority, todo.getPriority())
                .set(Todo::getDueDate, todo.getDueDate())
                .set(req.getProjectId() != null, Todo::getProjectId, todo.getProjectId())
                .set(req.getTeamId() != null, Todo::getTeamId, todo.getTeamId())
                .set(req.getSectionId() != null, Todo::getSectionId, todo.getSectionId())
                .set(effectiveTeamId != null, Todo::getAssigneeId, newAssigneeId)
                .eq(Todo::getId, todo.getId()));
        Long projectId = resolveProjectId(todo);
        if (projectId != null) {
            activityLogService.log(projectId, userId, "update", "Todo", todoId, "更新待办: " + req.getText());
            broadcastProjectUpdate(projectId);
        }
        // 负责人变更时推送通知
        if (newAssigneeId != null && !newAssigneeId.equals(oldAssigneeId)) {
            notifyAssignee(newAssigneeId, projectId, todoId, todo.getText());
        }
        return toResponse(todo,userId);
    }

    public TodoResponse toggleComplete(Long todoId, Long userId) {
        Todo todo = getAndAssertAccess(todoId, userId, false);
        todo.setCompleted(!todo.getCompleted());
        todoMapper.updateById(todo);
        Long projectId = resolveProjectId(todo);
        if (projectId != null) {
            activityLogService.log(projectId, userId, "complete", "Todo", todoId,
                    todo.getCompleted() ? "完成待办: " + todo.getText() : "重开待办: " + todo.getText());
            broadcastProjectUpdate(projectId);
        }
        return toResponse(todo,userId);
    }

    public void delete(Long todoId, Long userId) {
        Todo todo = getAndAssertAccess(todoId, userId, true);
        todoMapper.update(null, new LambdaUpdateWrapper<Todo>()
                .set(Todo::getDeletedTime, LocalDateTime.now())
                .set(Todo::getDeleted, 1)
                .eq(Todo::getId, todo.getId()));
        Long projectId = resolveProjectId(todo);
        if (projectId != null) {
            activityLogService.log(projectId, userId, "delete", "Todo", todoId, "删除待办: " + todo.getText());
            broadcastProjectUpdate(projectId);
        }
    }

    public void restore(Long todoId, Long userId) {
        Todo todo = todoMapper.selectByIdIgnoreLogic(todoId);
        if (todo == null || todo.getDeletedTime() == null) throw AppException.notFound("已删除的待办");
        if (!todo.getOwnerId().equals(userId)) throw AppException.forbidden();
        todoMapper.restoreById(todoId);
        if (todo.getProjectId() != null) {
            broadcastProjectUpdate(todo.getProjectId());
        }
    }

    public void permanentlyDelete(Long todoId, Long userId) {
        Todo todo = todoMapper.selectByIdIgnoreLogic(todoId);
        if (todo == null || todo.getDeletedTime() == null) throw AppException.notFound("已删除的待办");
        if (!todo.getOwnerId().equals(userId)) throw AppException.forbidden();
        todoMapper.forceDelete(todoId);
        if (todo.getProjectId() != null) {
            broadcastProjectUpdate(todo.getProjectId());
        }
    }

    public List<TodoResponse> listDeleted(Long userId) {
        List<Todo> todos = todoMapper.selectDeleted(userId);
        Map<Long, User> userMap = batchLoadUsers(todos);
        Map<Long, Project> projectMap = batchLoadProjects(todos);
        Map<Long, Team> teamMap = batchLoadTeams(todos);
        Map<Long, Section> sectionMap = batchLoadSections(todos);
        return todos.stream()
                .map(t -> toResponseSkippingTags(t, userMap, projectMap, teamMap, sectionMap)).collect(Collectors.toList());
    }

    private TodoResponse toResponseSkippingTags(Todo t, Map<Long, User> userMap, Map<Long, Project> projectMap, Map<Long, Team> teamMap, Map<Long, Section> sectionMap) {
        TodoResponse r = baseResponse(t);
        if (t.getAssigneeId() != null) {
            User assignee = userMap.get(t.getAssigneeId());
            r.setAssigneeName(assignee != null ? assignee.getDisplayName() : null);
        }
        r.setTags(Collections.emptyList());
        if (t.getProjectId() != null) {
            Project project = projectMap.get(t.getProjectId());
            r.setProjectName(project != null ? project.getName() : null);
        }
        if (t.getTeamId() != null) {
            Team team = teamMap.get(t.getTeamId());
            r.setTeamName(team != null ? team.getName() : null);
        }
        if (t.getSectionId() != null) {
            Section section = sectionMap.get(t.getSectionId());
            r.setSectionName(section != null ? section.getName() : null);
        }
        r.setCreateTime(t.getCreateTime());
        r.setUpdateTime(t.getUpdateTime());
        r.setDeletedTime(t.getDeletedTime());
        return r;
    }

    @Transactional
    public synchronized void reorder(Long userId, List<ReorderItem> items) {
        List<Long> ids = items.stream().map(ReorderItem::getId).collect(Collectors.toList());
        java.util.Map<Long, Todo> todoMap = todoMapper.selectBatchIds(ids).stream()
                .filter(t -> t != null)
                .collect(Collectors.toMap(Todo::getId, t -> t));
        Long projectId = null;
        for (ReorderItem item : items) {
            Todo todo = todoMap.get(item.getId());
            if (todo != null && (todo.getOwnerId().equals(userId) || isMember(todo.getTeamId(), userId))) {
                todoMapper.updateSortOrder(item.getId(), item.getSortOrder());
                if (projectId == null) projectId = todo.getProjectId();
            }
        }
        if (projectId != null) broadcastProjectUpdate(projectId);
    }

    // ========== 新增：移动 todo 到其他分区 ==========
    public TodoResponse moveSection(Long todoId, Long sectionId, Long userId) {
        Todo todo = getAndAssertAccess(todoId, userId, false);
        Long oldSectionId = todo.getSectionId();

        todo.setSectionId(sectionId);
        todoMapper.update(null, new LambdaUpdateWrapper<Todo>()
                .set(Todo::getSectionId, sectionId)
                .eq(Todo::getId, todo.getId()));

        // 自动化规则：移入/移出"已完成"分区自动切换完成状态
        boolean wasInDoneSection = isDoneSection(oldSectionId);
        boolean nowInDoneSection = isDoneSection(sectionId);

        if (nowInDoneSection && !todo.getCompleted()) {
            todo.setCompleted(true);
            todoMapper.update(null, new LambdaUpdateWrapper<Todo>()
                    .set(Todo::getCompleted, true)
                    .eq(Todo::getId, todoId));
        } else if (wasInDoneSection && !nowInDoneSection && todo.getCompleted()) {
            todo.setCompleted(false);
            todoMapper.update(null, new LambdaUpdateWrapper<Todo>()
                    .set(Todo::getCompleted, false)
                    .eq(Todo::getId, todoId));
        }

        Long projectId = resolveProjectId(todo);
        if (projectId != null) {
            activityLogService.log(projectId, userId, "move", "Todo", todoId, "移动待办到分区: " + sectionId);
            broadcastProjectUpdate(projectId);
        }
        return toResponse(todo,userId);
    }

    private boolean isDoneSection(Long sectionId) {
        if (sectionId == null) return false;
        Section section = sectionMapper.selectById(sectionId);
        if (section == null) return false;
        String name = section.getName();
        return name.contains("已完成") || name.contains("Done");
    }

    // TODO: LIKE '%keyword%' causes full table scan. Add FULLTEXT INDEX on todos(text) and
    // switch to MATCH AGAINST for better performance on large datasets.
    public List<TodoSearchResult> search(Long userId, String keyword) {
        if (!StringUtils.hasText(keyword)) return Collections.emptyList();
        List<Long> teamIds = teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getUserId, userId)
                        .eq(TeamMember::getDeleted, 0))
                .stream().map(TeamMember::getTeamId).collect(Collectors.toList());
        List<Todo> todos = todoMapper.selectList(new LambdaQueryWrapper<Todo>()
                .like(Todo::getText, "%" + keyword + "%")
                .isNull(Todo::getDeletedTime)
                .and(w -> w.eq(Todo::getOwnerId, userId).or().in(teamIds.size() > 0, Todo::getTeamId, teamIds))
                .orderByDesc(Todo::getUpdateTime)
                .last("LIMIT 20"));
        Set<Long> projectIds = todos.stream().map(Todo::getProjectId).filter(id -> id != null).collect(Collectors.toSet());
        Map<Long, Project> projectMap = projectIds.isEmpty() ? Collections.emptyMap()
                : projectMapper.selectBatchIds(projectIds).stream()
                        .filter(p -> p != null)
                        .collect(Collectors.toMap(Project::getId, p -> p));
        Set<Long> teamIdSet = todos.stream().map(Todo::getTeamId).filter(id -> id != null).collect(Collectors.toSet());
        Map<Long, Team> teamMap = teamIdSet.isEmpty() ? Collections.emptyMap()
                : teamMapper.selectBatchIds(teamIdSet).stream()
                        .filter(t -> t != null)
                        .collect(Collectors.toMap(Team::getId, t -> t));
        Set<Long> sectionIdSet = todos.stream().map(Todo::getSectionId).filter(id -> id != null).collect(Collectors.toSet());
        Map<Long, Section> sectionMap = sectionIdSet.isEmpty() ? Collections.emptyMap()
                : sectionMapper.selectBatchIds(sectionIdSet).stream()
                        .filter(s -> s != null)
                        .collect(Collectors.toMap(Section::getId, s -> s));
        Set<Long> assigneeIdSet = todos.stream().map(Todo::getAssigneeId).filter(id -> id != null).collect(Collectors.toSet());
        Map<Long, User> userMap = assigneeIdSet.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(assigneeIdSet).stream()
                        .filter(u -> u != null)
                        .collect(Collectors.toMap(User::getId, u -> u));
        return todos.stream().map(todo -> {
            TodoSearchResult r = new TodoSearchResult();
            r.setId(todo.getId());
            r.setText(todo.getText());
            r.setProjectId(todo.getProjectId());
            r.setTeamId(todo.getTeamId());
            r.setSectionId(todo.getSectionId());
            r.setAssigneeId(todo.getAssigneeId());
            r.setCompleted(todo.getCompleted());
            r.setDueDate(todo.getDueDate());
            if (todo.getProjectId() != null) {
                Project project = projectMap.get(todo.getProjectId());
                r.setProjectName(project != null ? project.getName() : null);
            }
            if (todo.getTeamId() != null) {
                Team team = teamMap.get(todo.getTeamId());
                r.setTeamName(team != null ? team.getName() : null);
            }
            if (todo.getSectionId() != null) {
                Section section = sectionMap.get(todo.getSectionId());
                r.setSectionName(section != null ? section.getName() : null);
            }
            if (todo.getAssigneeId() != null) {
                User assignee = userMap.get(todo.getAssigneeId());
                r.setAssigneeName(assignee != null ? assignee.getDisplayName() : null);
            }
            return r;
        }).collect(Collectors.toList());
    }

    public List<TodoResponse> export(TodoListQuery query) {
        return list(query);
    }

    public Todo getAndAssertAccess(Long todoId, Long userId, boolean requireAdminForTeam) {
        Todo todo = todoMapper.selectById(todoId);
        if (todo == null) throw AppException.notFound("待办事项");

        // 如果属于项目，走项目权限
        if (todo.getProjectId() != null) {
            assertProjectAccess(todo.getProjectId(), userId);
            // 项目内删除他人 todo 需要 ADMIN+
            if (requireAdminForTeam && !todo.getOwnerId().equals(userId)) {
                Project project = projectMapper.selectById(todo.getProjectId());
                if (project != null) {
                    List<ProjectTeam> ptList = projectTeamMapper.selectList(
                        new LambdaQueryWrapper<ProjectTeam>().eq(ProjectTeam::getProjectId, project.getId()));
                    if (!ptList.isEmpty()) {
                        boolean isAdmin = false;
                        for (ProjectTeam pt : ptList) {
                            TeamMember member = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                                .eq(TeamMember::getTeamId, pt.getTeamId())
                                .eq(TeamMember::getUserId, userId));
                            if (member != null && !"MEMBER".equals(member.getRole())) {
                                isAdmin = true; break;
                            }
                        }
                        if (!isAdmin) throw AppException.forbidden();
                    }
                }
            }
            return todo;
        }

        // 原有逻辑
        if (todo.getTeamId() == null) {
            if (!todo.getOwnerId().equals(userId)) throw AppException.forbidden();
        } else {
            TeamMember member = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                    .eq(TeamMember::getTeamId, todo.getTeamId())
                    .eq(TeamMember::getUserId, userId));
            if (member == null) throw AppException.forbidden();
            if (requireAdminForTeam && "MEMBER".equals(member.getRole()) && !todo.getOwnerId().equals(userId)) {
                throw AppException.forbidden();
            }
        }
        return todo;
    }

    public TodoResponse getById(Long todoId, Long userId) {
        Todo todo = getAndAssertAccess(todoId, userId, false);
        return toResponse(todo, userId);
    }

    // ========== 新增：校验项目访问权限 ==========
    private void assertProjectAccess(Long projectId, Long userId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) throw AppException.notFound("项目");
        List<ProjectTeam> ptList = projectTeamMapper.selectList(
            new LambdaQueryWrapper<ProjectTeam>().eq(ProjectTeam::getProjectId, projectId));
        if (ptList.isEmpty()) {
            if (!project.getOwnerId().equals(userId)) throw AppException.forbidden();
        } else {
            boolean isMember = false;
            for (ProjectTeam pt : ptList) {
                if (isMember(pt.getTeamId(), userId)) { isMember = true; break; }
            }
            if (!isMember) throw AppException.forbidden();
        }
    }

    private void assertTeamMember(Long teamId, Long userId) {
        if (!isMember(teamId, userId)) throw AppException.forbidden();
    }

    private boolean isMember(Long teamId, Long userId) {
        if (teamId == null) return false;
        return teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId)) != null;
    }

    private void applyAssignee(Todo todo, Long teamId, Long operatorId, Long assigneeId) {
        // 个人项目不支持分配人
        if (teamId == null) return;
        if (assigneeId != null && !isMember(teamId, assigneeId)) {
            throw AppException.badRequest("指定人不是团队成员");
        }
        todo.setAssigneeId(assigneeId);
    }

    private synchronized int nextSortOrder(Long userId, Long teamId, Long projectId) {
        LambdaQueryWrapper<Todo> w = new LambdaQueryWrapper<Todo>()
                .isNull(Todo::getDeletedTime)
                .eq(projectId != null, Todo::getProjectId, projectId)
                .eq(teamId != null, Todo::getTeamId, teamId)
                .isNull(teamId == null && projectId == null, Todo::getTeamId)
                .eq(teamId == null && projectId == null, Todo::getOwnerId, userId)
                .orderByDesc(Todo::getSortOrder)
                .last("LIMIT 1");
        Todo last = todoMapper.selectOne(w);
        return last == null ? 0 : last.getSortOrder() + 1;
    }

    /** Single-item toResponse (used by create/update/toggleComplete/moveSection). */
    private TodoResponse toResponse(Todo t, Long userId) {
        TodoResponse r = baseResponse(t);
        if (t.getAssigneeId() != null) {
            User assignee = userMapper.selectById(t.getAssigneeId());
            r.setAssigneeName(assignee != null ? assignee.getDisplayName() : null);
        }
        resolveProjectAndTeamNames(r, t);
        r.setTags(tagService.getTagsForTodo(t.getId(), userId));
        r.setCreateTime(t.getCreateTime());
        r.setUpdateTime(t.getUpdateTime());
        r.setDeletedTime(t.getDeletedTime());
        return r;
    }

    /** Batch toResponse with pre-loaded maps (used by list/listByProject). */
    private TodoResponse toResponse(Todo t,
                                     Map<Long, User> userMap,
                                     Map<Long, Project> projectMap,
                                     Map<Long, Team> teamMap,
                                     Map<Long, Section> sectionMap,
                                     Map<Long, List<TagResponse>> tagMap) {
        TodoResponse r = baseResponse(t);
        if (t.getAssigneeId() != null) {
            User assignee = userMap.get(t.getAssigneeId());
            r.setAssigneeName(assignee != null ? assignee.getDisplayName() : null);
        }
        if (t.getProjectId() != null) {
            Project project = projectMap.get(t.getProjectId());
            r.setProjectName(project != null ? project.getName() : null);
        }
        if (t.getTeamId() != null) {
            Team team = teamMap.get(t.getTeamId());
            r.setTeamName(team != null ? team.getName() : null);
        }
        if (t.getSectionId() != null) {
            Section section = sectionMap.get(t.getSectionId());
            r.setSectionName(section != null ? section.getName() : null);
        }
        r.setTags(tagMap.getOrDefault(t.getId(), Collections.emptyList()));
        r.setCreateTime(t.getCreateTime());
        r.setUpdateTime(t.getUpdateTime());
        r.setDeletedTime(t.getDeletedTime());
        return r;
    }

    private TodoResponse baseResponse(Todo t) {
        TodoResponse r = new TodoResponse();
        r.setId(t.getId());
        r.setText(t.getText());
        r.setCompleted(t.getCompleted());
        r.setCategory(t.getCategory());
        r.setPriority(t.getPriority());
        r.setDueDate(t.getDueDate());
        r.setSortOrder(t.getSortOrder());
        r.setOwnerId(t.getOwnerId());
        r.setTeamId(t.getTeamId());
        r.setProjectId(t.getProjectId());
        r.setSectionId(t.getSectionId());
        r.setAssigneeId(t.getAssigneeId());
        return r;
    }

    private Map<Long, List<TagResponse>> batchLoadTags(List<Todo> todos) {
        List<Long> todoIds = todos.stream().map(Todo::getId).collect(Collectors.toList());
        return tagService.getTagsForTodos(todoIds, null);
    }

    private Map<Long, User> batchLoadUsers(List<Todo> todos) {
        Set<Long> ids = todos.stream().map(Todo::getAssigneeId).filter(id -> id != null).collect(Collectors.toSet());
        if (ids.isEmpty()) return Collections.emptyMap();
        return userMapper.selectBatchIds(ids).stream()
                .filter(u -> u != null)
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private Map<Long, Project> batchLoadProjects(List<Todo> todos) {
        Set<Long> ids = todos.stream().map(Todo::getProjectId).filter(id -> id != null).collect(Collectors.toSet());
        if (ids.isEmpty()) return Collections.emptyMap();
        return projectMapper.selectBatchIds(ids).stream()
                .filter(p -> p != null)
                .collect(Collectors.toMap(Project::getId, p -> p));
    }

    private Map<Long, Team> batchLoadTeams(List<Todo> todos) {
        Set<Long> ids = todos.stream().map(Todo::getTeamId).filter(id -> id != null).collect(Collectors.toSet());
        if (ids.isEmpty()) return Collections.emptyMap();
        return teamMapper.selectBatchIds(ids).stream()
                .filter(t -> t != null)
                .collect(Collectors.toMap(Team::getId, t -> t));
    }

    private Map<Long, Section> batchLoadSections(List<Todo> todos) {
        Set<Long> ids = todos.stream().map(Todo::getSectionId).filter(id -> id != null).collect(Collectors.toSet());
        if (ids.isEmpty()) return Collections.emptyMap();
        return sectionMapper.selectBatchIds(ids).stream()
                .filter(s -> s != null)
                .collect(Collectors.toMap(Section::getId, s -> s));
    }

    private Long resolveProjectId(Todo todo) {
        return todo.getProjectId();
    }

    /** 当创建任务未指定 sectionId 时，取项目的第一个分区作为默认值 */
    private Long resolveDefaultSectionId(Long projectId) {
        if (projectId == null) return null;
        List<Section> sections = sectionMapper.selectList(
            new LambdaQueryWrapper<Section>()
                .eq(Section::getProjectId, projectId)
                .orderByAsc(Section::getSortOrder)
                .last("LIMIT 1")
        );
        return sections.isEmpty() ? null : sections.get(0).getId();
    }

    private void resolveProjectAndTeamNames(TodoResponse r, Todo t) {
        if (t.getProjectId() != null) {
            Project project = projectMapper.selectById(t.getProjectId());
            r.setProjectName(project != null ? project.getName() : null);
        }
        if (t.getTeamId() != null) {
            Team team = teamMapper.selectById(t.getTeamId());
            r.setTeamName(team != null ? team.getName() : null);
        }
        if (t.getSectionId() != null) {
            Section section = sectionMapper.selectById(t.getSectionId());
            r.setSectionName(section != null ? section.getName() : null);
        }
    }
}