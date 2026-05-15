package com.todo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.todo.dto.response.ReportListResponse;
import com.todo.dto.response.ReportResponse;
import com.todo.entity.ProjectTeam;
import com.todo.entity.*;
import com.todo.exception.AppException;
import com.todo.mapper.ProjectTeamMapper;
import com.todo.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportMapper reportMapper;
    private final TodoMapper todoMapper;
    private final ProjectMapper projectMapper;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final ActivityLogMapper activityLogMapper;
    private final UserMapper userMapper;
    private final ProjectTeamMapper projectTeamMapper;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public void generateAll(Long userId, String type) {
        int success = 0, failed = 0;

        // 1. 个人报告
        try {
            generateSelfReport(userId, type, null);
            success++;
        } catch (Exception e) {
            failed++;
        }

        // 2. 每个团队的执行人报告
        List<TeamMember> myMemberships = teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getUserId, userId)
                        .eq(TeamMember::getDeleted, 0));
        List<Long> allTeamIds = new ArrayList<>(myMemberships.stream().map(TeamMember::getTeamId).collect(Collectors.toList()));
        List<TeamMember> adminMemberships = teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getUserId, userId)
                        .in(TeamMember::getRole, "OWNER", "ADMIN")
                        .eq(TeamMember::getDeleted, 0));
        adminMemberships.forEach(tm -> { if (!allTeamIds.contains(tm.getTeamId())) allTeamIds.add(tm.getTeamId()); });
        java.util.Map<Long, Team> teamMap = allTeamIds.isEmpty() ? Collections.emptyMap()
                : teamMapper.selectBatchIds(allTeamIds).stream()
                        .filter(t -> t != null && t.getDeleted() == 0)
                        .collect(Collectors.toMap(Team::getId, t -> t));

        for (TeamMember tm : myMemberships) {
            if (teamMap.containsKey(tm.getTeamId())) {
                try {
                    generateSelfReport(userId, type, tm.getTeamId());
                    success++;
                } catch (Exception e) {
                    failed++;
                }
            }
        }

        // 3. 领导报告（用户是 OWNER/ADMIN 的团队）
        for (TeamMember tm : adminMemberships) {
            if (teamMap.containsKey(tm.getTeamId())) {
                try {
                    generateTeamReport(userId, tm.getTeamId(), type);
                    success++;
                } catch (Exception e) {
                    failed++;
                }
            }
        }

        if (success == 0) {
            throw new AppException("暂无数据可生成报告", HttpStatus.BAD_REQUEST);
        }
    }

    @Transactional
    public void generateSelfReport(Long userId, String type, Long teamId) {
        LocalDate today = LocalDate.now();
        LocalDate periodStart = "DAILY".equals(type) ? today : today.minusDays(6);
        LocalDate periodEnd = today;

        List<Long> projectIds = getProjectIds(userId, teamId);
        if (projectIds.isEmpty()) {
            throw new AppException("暂无项目数据，无法生成执行人报告", HttpStatus.BAD_REQUEST);
        }

        // 查询任务
        LambdaQueryWrapper<Todo> todoWrapper = new LambdaQueryWrapper<Todo>()
                .in(Todo::getProjectId, projectIds)
                .eq(Todo::getDeleted, 0);
        if (teamId == null) {
            todoWrapper.eq(Todo::getOwnerId, userId);
        } else {
            todoWrapper.eq(Todo::getAssigneeId, userId)
                      .eq(Todo::getTeamId, teamId);
        }
        List<Todo> allTodos = todoMapper.selectList(todoWrapper);

        if (allTodos.isEmpty()) {
            throw new AppException("本期暂无任务数据，无法生成执行人报告", HttpStatus.BAD_REQUEST);
        }

        // Single-pass aggregation: build all collections and counts in one iteration
        List<Todo> completed = new ArrayList<>();
        List<Todo> created = new ArrayList<>();
        List<Todo> overdue = new ArrayList<>();
        List<Todo> active = new ArrayList<>();
        Map<Long, long[]> projectAgg = new LinkedHashMap<>();  // pid -> [total, completed]
        Map<String, long[]> priorityAgg = new LinkedHashMap<>(); // priority -> [total, completed]
        for (String p : Arrays.asList("high", "medium", "low")) {
            priorityAgg.put(p, new long[]{0, 0});
        }
        for (Todo t : allTodos) {
            if (t.getCompleted()) {
                if (t.getUpdateTime() != null && !t.getUpdateTime().toLocalDate().isBefore(periodStart)) {
                    completed.add(t);
                }
            } else {
                active.add(t);
                if (t.getDueDate() != null && t.getDueDate().isBefore(today)) {
                    overdue.add(t);
                }
            }
            if (!t.getCreateTime().toLocalDate().isBefore(periodStart)) {
                created.add(t);
            }
            // project counts
            Long pid = t.getProjectId();
            projectAgg.computeIfAbsent(pid, k -> new long[]{0, 0});
            long[] pc = projectAgg.get(pid);
            pc[0]++;
            if (t.getCompleted()) pc[1]++;
            // priority counts
            String pri = t.getPriority();
            long[] prc = priorityAgg.get(pri);
            if (prc != null) {
                prc[0]++;
                if (t.getCompleted()) prc[1]++;
            }
        }

        // 项目名映射
        Map<Long, String> projectNameMap = projectMapper.selectBatchIds(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, Project::getName));

        Team team = teamId != null ? teamMapper.selectById(teamId) : null;
        String teamPrefix = team != null ? "[" + team.getName() + "] " : "";

        // 项目统计 (from pre-computed aggregates)
        List<Map<String, Object>> projectStats = projectIds.stream().map(pid -> {
            long[] counts = projectAgg.getOrDefault(pid, new long[]{0, 0});
            long total = counts[0];
            long done = counts[1];
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("projectId", pid);
            m.put("projectName", teamPrefix + projectNameMap.getOrDefault(pid, "未知项目"));
            m.put("total", total);
            m.put("completed", done);
            m.put("rate", total > 0 ? (double) done / total : 0);
            return m;
        }).collect(Collectors.toList());

        // 优先级统计 (from pre-computed aggregates)
        Map<String, Map<String, Long>> priorityStats = new LinkedHashMap<>();
        for (Map.Entry<String, long[]> e : priorityAgg.entrySet()) {
            Map<String, Long> s = new LinkedHashMap<>();
            s.put("total", e.getValue()[0]);
            s.put("completed", e.getValue()[1]);
            priorityStats.put(e.getKey(), s);
        }

        // json_data
        Map<String, Object> jsonData = new LinkedHashMap<>();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalCompleted", completed.size());
        summary.put("totalCreated", created.size());
        summary.put("totalOverdue", overdue.size());
        summary.put("totalActive", active.size());
        jsonData.put("summary", summary);

        jsonData.put("completedTasks", completed.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("text", t.getText());
            m.put("projectName", teamPrefix + projectNameMap.getOrDefault(t.getProjectId(), ""));
            m.put("priority", t.getPriority());
            m.put("completedAt", t.getUpdateTime() != null ? t.getUpdateTime().toString() : null);
            return m;
        }).collect(Collectors.toList()));

        jsonData.put("createdTasks", created.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("text", t.getText());
            m.put("projectName", teamPrefix + projectNameMap.getOrDefault(t.getProjectId(), ""));
            m.put("priority", t.getPriority());
            m.put("createdAt", t.getCreateTime().toString());
            return m;
        }).collect(Collectors.toList()));

        jsonData.put("overdueTasks", overdue.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("text", t.getText());
            m.put("dueDate", t.getDueDate() != null ? t.getDueDate().toString() : null);
            m.put("projectName", teamPrefix + projectNameMap.getOrDefault(t.getProjectId(), ""));
            return m;
        }).collect(Collectors.toList()));

        jsonData.put("projectStats", projectStats);
        jsonData.put("priorityStats", priorityStats);

        Map<String, Object> period = new LinkedHashMap<>();
        period.put("start", periodStart.toString());
        period.put("end", periodEnd.toString());
        jsonData.put("period", period);

        String preview = String.format("完成%d项，新增%d项，逾期%d项", completed.size(), created.size(), overdue.size());
        String content = buildMarkdown(type, periodStart, periodEnd, summary, completed, created, overdue, projectStats, teamPrefix, projectNameMap);
        String dateStr = periodStart.equals(periodEnd) ? periodStart.toString() : periodStart + "~" + periodEnd;
        String title = team != null ? team.getName() + " " + dateStr + ("DAILY".equals(type) ? "日报" : "周报")
                : dateStr + ("DAILY".equals(type) ? "日报" : "周报");

        Report report = saveReport(userId, type, "SELF", teamId, title, preview, content, jsonData, periodStart, periodEnd);

        String notifType = teamId != null ? "REPORT_TEAM_" + type : "REPORT_" + type;
        notificationService.create(userId, notifType, title + "已生成", preview, "/reports?reportId=" + report.getId());
    }

    @Transactional
    public void generateTeamReport(Long userId, Long teamId, String type) {
        TeamMember member = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getUserId, userId)
                .eq(TeamMember::getTeamId, teamId)
                .in(TeamMember::getRole, "OWNER", "ADMIN")
                .eq(TeamMember::getDeleted, 0));
        if (member == null) {
            throw new AppException("无权生成该团队的领导报告", HttpStatus.FORBIDDEN);
        }

        LocalDate today = LocalDate.now();
        LocalDate periodStart = "DAILY".equals(type) ? today : today.minusDays(6);
        LocalDate periodEnd = today;

        Team team = teamMapper.selectById(teamId);
        if (team == null) throw AppException.notFound("团队");

        List<ProjectTeam> ptList = projectTeamMapper.selectList(
                new LambdaQueryWrapper<ProjectTeam>().eq(ProjectTeam::getTeamId, teamId));
        List<Long> projectIds = ptList.stream().map(ProjectTeam::getProjectId).collect(Collectors.toList());
        List<Project> teamProjects = projectIds.isEmpty() ? Collections.emptyList()
                : projectMapper.selectBatchIds(projectIds);

        if (projectIds.isEmpty()) {
            throw new AppException("该团队暂无项目数据，无法生成领导报告", HttpStatus.BAD_REQUEST);
        }

        List<TeamMember> allMembers = teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getDeleted, 0));
        List<Long> memberUserIds = allMembers.stream().map(TeamMember::getUserId).collect(Collectors.toList());
        Map<Long, User> userMap = memberUserIds.isEmpty() ? Collections.emptyMap() :
                userMapper.selectBatchIds(memberUserIds).stream().collect(Collectors.toMap(User::getId, u -> u));

        List<Todo> allTodos = todoMapper.selectList(new LambdaQueryWrapper<Todo>()
                .in(Todo::getProjectId, projectIds)
                .eq(Todo::getTeamId, teamId)
                .eq(Todo::getDeleted, 0));

        if (allTodos.isEmpty()) {
            throw new AppException("该团队本期暂无任务数据，无法生成领导报告", HttpStatus.BAD_REQUEST);
        }

        // Single-pass aggregation
        List<Todo> completed = new ArrayList<>();
        List<Todo> overdue = new ArrayList<>();
        List<Todo> active = new ArrayList<>();
        int totalCreated = 0;
        Map<Long, long[]> memberAgg = new LinkedHashMap<>();  // uid -> [completed, overdue, active]
        Map<Long, long[]> projectAgg = new LinkedHashMap<>();  // pid -> [total, completed]
        Map<String, long[]> priorityAgg = new LinkedHashMap<>(); // priority -> [total, completed]
        for (String p : Arrays.asList("high", "medium", "low")) {
            priorityAgg.put(p, new long[]{0, 0});
        }
        for (Todo t : allTodos) {
            boolean isCompleted = t.getCompleted();
            boolean inPeriod = t.getUpdateTime() != null && !t.getUpdateTime().toLocalDate().isBefore(periodStart);
            if (isCompleted && inPeriod) completed.add(t);
            if (!isCompleted) {
                active.add(t);
                if (t.getDueDate() != null && t.getDueDate().isBefore(today)) overdue.add(t);
            }
            if (!t.getCreateTime().toLocalDate().isBefore(periodStart)) totalCreated++;
            // member aggregates
            Long aid = t.getAssigneeId();
            if (aid != null) {
                memberAgg.computeIfAbsent(aid, k -> new long[]{0, 0, 0});
                long[] ma = memberAgg.get(aid);
                if (isCompleted && inPeriod) ma[0]++;
                if (!isCompleted && t.getDueDate() != null && t.getDueDate().isBefore(today)) ma[1]++;
                if (!isCompleted) ma[2]++;
            }
            // project aggregates
            Long pid = t.getProjectId();
            projectAgg.computeIfAbsent(pid, k -> new long[]{0, 0});
            long[] pc = projectAgg.get(pid);
            pc[0]++;
            if (isCompleted) pc[1]++;
            // priority aggregates
            String pri = t.getPriority();
            long[] prc = priorityAgg.get(pri);
            if (prc != null) {
                prc[0]++;
                if (isCompleted) prc[1]++;
            }
        }

        List<Map<String, Object>> memberStats = memberUserIds.stream().map(uid -> {
            long[] ma = memberAgg.getOrDefault(uid, new long[]{0, 0, 0});
            User u = userMap.get(uid);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", uid);
            m.put("displayName", u != null ? u.getDisplayName() : "未知");
            m.put("completed", ma[0]);
            m.put("overdue", ma[1]);
            m.put("active", ma[2]);
            return m;
        }).collect(Collectors.toList());

        memberStats.sort((a, b) -> Long.compare((Long) b.get("completed"), (Long) a.get("completed")));

        List<Map<String, Object>> projectStats = teamProjects.stream().map(p -> {
            long[] counts = projectAgg.getOrDefault(p.getId(), new long[]{0, 0});
            long total = counts[0];
            long done = counts[1];
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("projectId", p.getId());
            m.put("projectName", p.getName());
            m.put("total", total);
            m.put("completed", done);
            m.put("rate", total > 0 ? (double) done / total : 0);
            return m;
        }).collect(Collectors.toList());

        Map<String, Map<String, Long>> priorityStats = new LinkedHashMap<>();
        for (Map.Entry<String, long[]> e : priorityAgg.entrySet()) {
            Map<String, Long> s = new LinkedHashMap<>();
            s.put("total", e.getValue()[0]);
            s.put("completed", e.getValue()[1]);
            priorityStats.put(e.getKey(), s);
        }

        Map<String, Object> jsonData = new LinkedHashMap<>();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalCompleted", completed.size());
        summary.put("totalActive", active.size());
        summary.put("totalOverdue", overdue.size());
        summary.put("totalMembers", memberUserIds.size());
        summary.put("totalCreated", totalCreated);
        jsonData.put("summary", summary);
        jsonData.put("memberStats", memberStats);
        jsonData.put("projectStats", projectStats);
        jsonData.put("priorityStats", priorityStats);
        jsonData.put("completedTasks", completed.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("text", t.getText());
            User u = userMap.get(t.getAssigneeId());
            m.put("assigneeName", u != null ? u.getDisplayName() : "未分配");
            m.put("priority", t.getPriority());
            m.put("completedAt", t.getUpdateTime() != null ? t.getUpdateTime().toString() : null);
            return m;
        }).collect(Collectors.toList()));
        jsonData.put("overdueTasks", overdue.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("text", t.getText());
            User u = userMap.get(t.getAssigneeId());
            m.put("assigneeName", u != null ? u.getDisplayName() : "未分配");
            m.put("dueDate", t.getDueDate() != null ? t.getDueDate().toString() : null);
            return m;
        }).collect(Collectors.toList()));
        Map<String, Object> period = new LinkedHashMap<>();
        period.put("start", periodStart.toString());
        period.put("end", periodEnd.toString());
        jsonData.put("period", period);

        String preview = String.format("完成%d项，%d名成员，逾期%d项", completed.size(), memberUserIds.size(), overdue.size());
        String dateStr = periodStart.equals(periodEnd) ? periodStart.toString() : periodStart + "~" + periodEnd;
        String title = team.getName() + " " + dateStr + ("DAILY".equals(type) ? "团队日报" : "团队周报");
        String content = buildTeamMarkdown(team.getName(), type, periodStart, periodEnd, summary, memberStats);

        Report report = saveReport(userId, type, "TEAM", teamId, title, preview, content, jsonData, periodStart, periodEnd);
        notificationService.create(userId, "REPORT_TEAM_" + type, title + "已生成", preview, "/reports?reportId=" + report.getId());
    }

    private Report saveReport(Long userId, String type, String scope, Long teamId, String title,
                              String preview, String content, Map<String, Object> jsonData,
                              LocalDate periodStart, LocalDate periodEnd) {
        // 删除旧记录（唯一约束去重）
        LambdaQueryWrapper<Report> dw = new LambdaQueryWrapper<Report>()
                .eq(Report::getUserId, userId)
                .eq(Report::getType, type)
                .eq(Report::getScope, scope)
                .eq(Report::getPeriodEnd, periodEnd);
        if (teamId == null) {
            dw.isNull(Report::getTeamId);
        } else {
            dw.eq(Report::getTeamId, teamId);
        }
        reportMapper.delete(dw);

        Report report = new Report();
        report.setUserId(userId);
        report.setType(type);
        report.setScope(scope);
        report.setTeamId(teamId);
        report.setTitle(title);
        report.setPreview(preview);
        report.setContent(content);
        try {
            report.setJsonData(objectMapper.writeValueAsString(jsonData));
        } catch (Exception e) {
            report.setJsonData("{}");
        }
        report.setPeriodStart(periodStart);
        report.setPeriodEnd(periodEnd);
        reportMapper.insert(report);
        return report;
    }

    private List<Long> getProjectIds(Long userId, Long teamId) {
        if (teamId != null) {
            List<ProjectTeam> ptList = projectTeamMapper.selectList(
                    new LambdaQueryWrapper<ProjectTeam>().eq(ProjectTeam::getTeamId, teamId));
            return ptList.stream().map(ProjectTeam::getProjectId).collect(Collectors.toList());
        } else {
            // Personal projects: owned by user and NOT associated with any team
            List<Project> owned = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                    .eq(Project::getOwnerId, userId)
                    .eq(Project::getDeleted, 0));
            List<Long> ownedIds = owned.stream().map(Project::getId).collect(Collectors.toList());
            if (ownedIds.isEmpty()) return Collections.emptyList();

            List<ProjectTeam> ptList = projectTeamMapper.selectList(
                    new LambdaQueryWrapper<ProjectTeam>().in(ProjectTeam::getProjectId, ownedIds));
            Set<Long> teamProjectIds = ptList.stream().map(ProjectTeam::getProjectId).collect(Collectors.toSet());

            return ownedIds.stream().filter(id -> !teamProjectIds.contains(id)).collect(Collectors.toList());
        }
    }

    public IPage<ReportListResponse> getReports(Long userId, String scope, String type, int page, int size) {
        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<Report>()
                .eq(Report::getUserId, userId)
                .eq(Report::getDeleted, 0);
        if (scope != null && !scope.isEmpty()) wrapper.eq(Report::getScope, scope);
        if (type != null && !type.isEmpty()) wrapper.eq(Report::getType, type);
        wrapper.orderByDesc(Report::getCreateTime);

        IPage<Report> pageResult = reportMapper.selectPage(new Page<>(page, size), wrapper);

        return pageResult.convert(r -> {
            ReportListResponse resp = new ReportListResponse();
            resp.setId(r.getId());
            resp.setTitle(r.getTitle());
            resp.setType(r.getType());
            resp.setScope(r.getScope());
            resp.setTeamId(r.getTeamId());
            if (r.getTeamId() != null) {
                Team t = teamMapper.selectById(r.getTeamId());
                resp.setTeamName(t != null ? t.getName() : null);
            }
            resp.setPreview(r.getPreview());
            resp.setPeriodStart(r.getPeriodStart());
            resp.setPeriodEnd(r.getPeriodEnd());
            resp.setCreateTime(r.getCreateTime());
            return resp;
        });
    }

    public ReportResponse getReport(Long id, Long userId) {
        Report report = reportMapper.selectById(id);
        if (report == null || report.getDeleted() == 1) throw AppException.notFound("报告");
        if (!report.getUserId().equals(userId)) throw AppException.forbidden();

        if ("TEAM".equals(report.getScope()) && report.getTeamId() != null) {
            TeamMember member = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                    .eq(TeamMember::getUserId, userId)
                    .eq(TeamMember::getTeamId, report.getTeamId())
                    .in(TeamMember::getRole, "OWNER", "ADMIN")
                    .eq(TeamMember::getDeleted, 0));
            if (member == null) throw AppException.forbidden();
        }

        ReportResponse resp = new ReportResponse();
        resp.setId(report.getId());
        resp.setUserId(report.getUserId());
        resp.setType(report.getType());
        resp.setScope(report.getScope());
        resp.setTeamId(report.getTeamId());
        if (report.getTeamId() != null) {
            Team t = teamMapper.selectById(report.getTeamId());
            resp.setTeamName(t != null ? t.getName() : null);
        }
        resp.setTitle(report.getTitle());
        resp.setPreview(report.getPreview());
        resp.setContent(report.getContent());
        resp.setPeriodStart(report.getPeriodStart());
        resp.setPeriodEnd(report.getPeriodEnd());
        resp.setCreateTime(report.getCreateTime());
        try {
            if (report.getJsonData() != null) {
                resp.setJsonData(objectMapper.readValue(report.getJsonData(), Map.class));
            }
        } catch (Exception ignored) {}
        return resp;
    }

    @Transactional
    public void deleteReport(Long id, Long userId) {
        Report report = reportMapper.selectById(id);
        if (report == null) throw AppException.notFound("报告");
        if (!report.getUserId().equals(userId)) throw AppException.forbidden();
        reportMapper.deleteById(id);
    }

    private String buildMarkdown(String type, LocalDate start, LocalDate end,
                                  Map<String, Object> summary, List<Todo> completed,
                                  List<Todo> created, List<Todo> overdue,
                                  List<Map<String, Object>> projectStats,
                                  String teamPrefix, Map<Long, String> projectNameMap) {
        StringBuilder sb = new StringBuilder();
        String title = (start.equals(end) ? start.toString() : start + "~" + end) + ("DAILY".equals(type) ? "日报" : "周报");
        sb.append("# ").append(title).append("\n\n");
        sb.append("## 概览\n\n");
        sb.append("- ✅ 已完成：").append(summary.get("totalCompleted")).append("\n");
        sb.append("- 📝 新增：").append(summary.get("totalCreated")).append("\n");
        sb.append("- ⏰ 逾期：").append(summary.get("totalOverdue")).append("\n");
        sb.append("- 📋 待办：").append(summary.get("totalActive")).append("\n\n");
        if (!completed.isEmpty()) {
            sb.append("## 完成任务\n\n| 任务 | 优先级 | 完成时间 |\n|------|--------|----------|\n");
            completed.forEach(t -> sb.append("| ").append(t.getText()).append(" | ").append(t.getPriority())
                    .append(" | ").append(t.getUpdateTime()).append(" |\n"));
            sb.append("\n");
        }
        if (!overdue.isEmpty()) {
            sb.append("## 逾期任务\n\n| 任务 | 截止日期 |\n|------|----------|\n");
            overdue.forEach(t -> sb.append("| ").append(t.getText()).append(" | ").append(t.getDueDate()).append(" |\n"));
            sb.append("\n");
        }
        return sb.toString();
    }

    private String buildTeamMarkdown(String teamName, String type, LocalDate start, LocalDate end,
                                      Map<String, Object> summary, List<Map<String, Object>> memberStats) {
        StringBuilder sb = new StringBuilder();
        String title = teamName + " " + (start.equals(end) ? start.toString() : start + "~" + end)
                + ("DAILY".equals(type) ? "团队日报" : "团队周报");
        sb.append("# ").append(title).append("\n\n");
        sb.append("## 团队概览\n\n");
        sb.append("- ✅ 完成：").append(summary.get("totalCompleted")).append("\n");
        sb.append("- ⏰ 逾期：").append(summary.get("totalOverdue")).append("\n");
        sb.append("- 👥 成员：").append(summary.get("totalMembers")).append("\n\n");
        if (!memberStats.isEmpty()) {
            sb.append("## 成员排行\n\n| 成员 | 完成 | 逾期 |\n|------|------|------|\n");
            memberStats.forEach(m -> sb.append("| ").append(m.get("displayName")).append(" | ")
                    .append(m.get("completed")).append(" | ").append(m.get("overdue")).append(" |\n"));
        }
        return sb.toString();
    }
}
