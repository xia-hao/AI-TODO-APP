package com.todo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.todo.entity.Project;
import com.todo.entity.ProjectTeam;
import com.todo.entity.Tag;
import com.todo.entity.TeamMember;
import com.todo.entity.Todo;
import com.todo.entity.TodoTag;
import com.todo.mapper.ProjectMapper;
import com.todo.mapper.ProjectTeamMapper;
import com.todo.mapper.TagMapper;
import com.todo.mapper.TeamMemberMapper;
import com.todo.mapper.TodoMapper;
import com.todo.mapper.TodoTagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final TodoMapper todoMapper;
    private final ProjectMapper projectMapper;
    private final ProjectTeamMapper projectTeamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final TodoTagMapper todoTagMapper;
    private final TagMapper tagMapper;

    /**
     * 获取用户可访问项目 ID，按个人项目(false)/团队项目(true) 分组
     */
    private Map<Boolean, List<Long>> getAccessibleProjectIds(Long userId) {
        List<Long> userTeamIds = teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getUserId, userId))
                .stream().map(TeamMember::getTeamId).collect(Collectors.toList());

        // Owned projects
        Set<Long> allProjectIds = projectMapper.selectList(
                new LambdaQueryWrapper<Project>().eq(Project::getOwnerId, userId))
                .stream().map(Project::getId).collect(Collectors.toSet());

        // Team projects from join table
        if (!userTeamIds.isEmpty()) {
            projectTeamMapper.selectList(
                    new LambdaQueryWrapper<ProjectTeam>().in(ProjectTeam::getTeamId, userTeamIds))
                    .stream().map(ProjectTeam::getProjectId).forEach(allProjectIds::add);
        }

        // Classify: projects with any team association are "team projects"
        Set<Long> projectsWithTeams = Collections.emptySet();
        if (!allProjectIds.isEmpty()) {
            projectsWithTeams = projectTeamMapper.selectList(
                    new LambdaQueryWrapper<ProjectTeam>().in(ProjectTeam::getProjectId, allProjectIds))
                    .stream().map(ProjectTeam::getProjectId).collect(Collectors.toSet());
        }

        List<Long> personalIds = new ArrayList<>();
        List<Long> teamIds = new ArrayList<>();
        for (Long pid : allProjectIds) {
            if (projectsWithTeams.contains(pid)) teamIds.add(pid);
            else personalIds.add(pid);
        }
        Map<Boolean, List<Long>> result = new HashMap<>();
        result.put(Boolean.FALSE, personalIds);
        result.put(Boolean.TRUE, teamIds);
        return result;
    }

    public Map<String, Object> overview(Long userId) {
        Map<Boolean, List<Long>> ids = getAccessibleProjectIds(userId);
        List<Long> personalIds = ids.get(Boolean.FALSE);
        List<Long> teamIds = ids.get(Boolean.TRUE);
        LocalDate today = LocalDate.now();

        long total = countByProjectIds(personalIds, userId, true, null, null)
                   + countByProjectIds(teamIds, userId, false, null, null);
        long completed = countByProjectIds(personalIds, userId, true, true, null)
                       + countByProjectIds(teamIds, userId, false, true, null);
        long active = total - completed;
        long overdue = countByProjectIds(personalIds, userId, true, null, today)
                     + countByProjectIds(teamIds, userId, false, null, today);

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("completed", completed);
        stats.put("active", active);
        stats.put("overdue", overdue);
        return stats;
    }

    private long countByProjectIds(List<Long> projectIds, Long userId, boolean isOwner,
                                    Boolean completed, LocalDate overdueBefore) {
        if (projectIds == null || projectIds.isEmpty()) return 0;
        LambdaQueryWrapper<Todo> w = new LambdaQueryWrapper<Todo>()
                .in(Todo::getProjectId, projectIds)
                .eq(Todo::getDeleted, 0);
        if (isOwner) w.eq(Todo::getOwnerId, userId);
        else w.eq(Todo::getAssigneeId, userId);
        if (completed != null) w.eq(Todo::getCompleted, completed);
        if (overdueBefore != null) {
            w.eq(Todo::getCompleted, false).isNotNull(Todo::getDueDate).lt(Todo::getDueDate, overdueBefore);
        }
        return todoMapper.selectCount(w);
    }

    public List<Map<String, Object>> trend(Long userId, int days) {
        LocalDate start = LocalDate.now().minusDays(days - 1);
        Map<Boolean, List<Long>> ids = getAccessibleProjectIds(userId);
        List<Long> personalIds = ids.get(Boolean.FALSE);
        List<Long> teamIds = ids.get(Boolean.TRUE);

        List<Todo> completedTodos = new ArrayList<>();
        if (!personalIds.isEmpty()) {
            completedTodos.addAll(todoMapper.selectList(new LambdaQueryWrapper<Todo>()
                    .in(Todo::getProjectId, personalIds)
                    .eq(Todo::getOwnerId, userId)
                    .eq(Todo::getCompleted, true)
                    .ge(Todo::getUpdateTime, start.atStartOfDay())));
        }
        if (!teamIds.isEmpty()) {
            completedTodos.addAll(todoMapper.selectList(new LambdaQueryWrapper<Todo>()
                    .in(Todo::getProjectId, teamIds)
                    .eq(Todo::getAssigneeId, userId)
                    .eq(Todo::getCompleted, true)
                    .ge(Todo::getUpdateTime, start.atStartOfDay())));
        }

        Map<LocalDate, Long> counts = completedTodos.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getUpdateTime().toLocalDate(),
                        Collectors.counting()
                ));

        List<Map<String, Object>> result = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(LocalDate.now()); date = date.plusDays(1)) {
            Map<String, Object> item = new HashMap<>();
            item.put("date", date.toString());
            item.put("count", counts.getOrDefault(date, 0L));
            result.add(item);
        }
        return result;
    }

    public List<Todo> upcoming(Long userId, int limit) {
        Map<Boolean, List<Long>> ids = getAccessibleProjectIds(userId);
        List<Long> personalIds = ids.get(Boolean.FALSE);
        List<Long> teamIds = ids.get(Boolean.TRUE);

        // Single query using OR: (personal projects AND owner) OR (team projects AND assignee)
        LambdaQueryWrapper<Todo> wrapper = new LambdaQueryWrapper<Todo>()
                .eq(Todo::getCompleted, false)
                .isNotNull(Todo::getDueDate)
                .ge(Todo::getDueDate, LocalDate.now())
                .eq(Todo::getDeleted, 0)
                .and(w -> {
                    if (!personalIds.isEmpty()) {
                        w.in(Todo::getProjectId, personalIds).eq(Todo::getOwnerId, userId);
                    }
                    if (!teamIds.isEmpty()) {
                        if (!personalIds.isEmpty()) w.or();
                        w.in(Todo::getProjectId, teamIds).eq(Todo::getAssigneeId, userId);
                    }
                })
                .orderByAsc(Todo::getDueDate)
                .last("LIMIT " + limit);

        return todoMapper.selectList(wrapper);
    }

    public List<Map<String, Object>> projectStats(Long userId) {
        Map<Boolean, List<Long>> ids = getAccessibleProjectIds(userId);
        List<Long> personalIds = ids.get(Boolean.FALSE);
        List<Long> teamIds = ids.get(Boolean.TRUE);

        List<Long> allIds = new ArrayList<>();
        allIds.addAll(personalIds);
        allIds.addAll(teamIds);
        if (allIds.isEmpty()) return Collections.emptyList();

        List<Project> projects = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .in(Project::getId, allIds));
        Map<Long, Project> projectMap = projects.stream().collect(Collectors.toMap(Project::getId, p -> p));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, Project> entry : projectMap.entrySet()) {
            Long pid = entry.getKey();
            boolean isTeam = teamIds.contains(pid);
            long total = todoMapper.selectCount(new LambdaQueryWrapper<Todo>()
                    .eq(Todo::getProjectId, pid)
                    .eq(Todo::getDeleted, 0)
                    .eq(isTeam ? Todo::getAssigneeId : Todo::getOwnerId, userId));
            long completed = todoMapper.selectCount(new LambdaQueryWrapper<Todo>()
                    .eq(Todo::getProjectId, pid)
                    .eq(Todo::getDeleted, 0)
                    .eq(Todo::getCompleted, true)
                    .eq(isTeam ? Todo::getAssigneeId : Todo::getOwnerId, userId));
            Map<String, Object> item = new HashMap<>();
            item.put("projectId", pid);
            item.put("projectName", entry.getValue().getName());
            item.put("total", total);
            item.put("completed", completed);
            item.put("rate", total > 0 ? (double) completed / total : 0);
            result.add(item);
        }
        result.sort((a, b) -> Double.compare((Double) b.get("rate"), (Double) a.get("rate")));
        return result;
    }

    public List<Map<String, Object>> assigneeStats(Long userId) {
        Map<Boolean, List<Long>> ids = getAccessibleProjectIds(userId);
        List<Long> personalIds = ids.get(Boolean.FALSE);
        List<Long> teamIds = ids.get(Boolean.TRUE);

        Map<Long, Long> assigneeCounts = new HashMap<>();
        if (!personalIds.isEmpty()) {
            selectAssigneeCounts(personalIds, userId, true).forEach((k, v) -> assigneeCounts.merge(k, v, Long::sum));
        }
        if (!teamIds.isEmpty()) {
            selectAssigneeCounts(teamIds, userId, false).forEach((k, v) -> assigneeCounts.merge(k, v, Long::sum));
        }
        return assigneeCounts.entrySet().stream().map(e -> {
            Map<String, Object> item = new HashMap<>();
            item.put("userId", e.getKey());
            item.put("count", e.getValue());
            return item;
        }).collect(Collectors.toList());
    }

    private Map<Long, Long> selectAssigneeCounts(List<Long> projectIds, Long userId, boolean isOwner) {
        Map<Long, Long> result = new HashMap<>();
        todoMapper.selectMaps(new QueryWrapper<Todo>()
                .select("assignee_id", "COUNT(*) as cnt")
                .in("project_id", projectIds)
                .eq(isOwner ? "owner_id" : "assignee_id", userId)
                .isNotNull("assignee_id")
                .eq("deleted", 0)
                .groupBy("assignee_id"))
                .forEach(row -> {
                    Long assigneeId = row.get("assignee_id") != null ? ((Number) row.get("assignee_id")).longValue() : null;
                    Long cnt = row.get("cnt") != null ? ((Number) row.get("cnt")).longValue() : 0L;
                    if (assigneeId != null) result.put(assigneeId, cnt);
                });
        return result;
    }

    public List<Map<String, Object>> tagUsageStats() {
        return todoTagMapper.selectList(new LambdaQueryWrapper<TodoTag>())
                .stream()
                .collect(Collectors.groupingBy(TodoTag::getTagId, Collectors.counting()))
                .entrySet().stream().limit(20).map(e -> {
                    Map<String, Object> item = new HashMap<>();
                    Tag tag = tagMapper.selectById(e.getKey());
                    if (tag != null) {
                        item.put("tagId", tag.getId());
                        item.put("tagName", tag.getName());
                        item.put("tagColor", tag.getColor());
                    }
                    item.put("count", e.getValue());
                    return item;
                }).collect(Collectors.toList());
    }
}
