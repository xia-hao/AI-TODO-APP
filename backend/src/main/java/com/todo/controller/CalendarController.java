package com.todo.controller;

import com.todo.dto.response.ApiResponse;
import com.todo.dto.response.CalendarEvent;
import com.todo.entity.Project;
import com.todo.entity.Todo;
import com.todo.entity.User;
import com.todo.mapper.ProjectMapper;
import com.todo.mapper.TodoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/todos/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final TodoMapper todoMapper;
    private final ProjectMapper projectMapper;

    @GetMapping
    public ApiResponse<List<CalendarEvent>> getEvents(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        LocalDate startDate = start != null ? LocalDate.parse(start) : LocalDate.now().minusMonths(1);
        LocalDate endDate = end != null ? LocalDate.parse(end) : LocalDate.now().plusMonths(2);

        List<Todo> todos = todoMapper.selectList(new LambdaQueryWrapper<Todo>()
                .eq(Todo::getOwnerId, user.getId())
                .isNull(Todo::getDeletedTime)
                .isNotNull(Todo::getDueDate)
                .ge(Todo::getDueDate, startDate)
                .le(Todo::getDueDate, endDate));

        return ApiResponse.ok(todos.stream().map(todo -> {
            CalendarEvent ev = new CalendarEvent();
            ev.setId(todo.getId());
            ev.setTitle(todo.getText());
            ev.setStart(todo.getDueDate());
            ev.setCompleted(todo.getCompleted());
            ev.setProjectId(todo.getProjectId());
            if (todo.getProjectId() != null) {
                Project project = projectMapper.selectById(todo.getProjectId());
                ev.setProjectName(project != null ? project.getName() : null);
            }
            if (todo.getCompleted()) {
                ev.setColor("#67c23a");
            } else if (todo.getDueDate() != null && todo.getDueDate().isBefore(LocalDate.now())) {
                ev.setColor("#f56c6c");
            } else {
                ev.setColor("#409eff");
            }
            return ev;
        }).collect(Collectors.toList()));
    }
}
