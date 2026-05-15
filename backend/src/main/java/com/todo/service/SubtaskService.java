package com.todo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.todo.dto.request.*;
import com.todo.dto.response.SubtaskResponse;
import com.todo.entity.Subtask;
import com.todo.entity.Todo;
import com.todo.entity.User;
import com.todo.exception.AppException;
import com.todo.mapper.SubtaskMapper;
import com.todo.mapper.TodoMapper;
import com.todo.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubtaskService {

    private final SubtaskMapper subtaskMapper;
    private final TodoMapper todoMapper;
    private final UserMapper userMapper;
    private final TodoService todoService;

    /**
     * 获取某个任务的所有子任务（按 sort_order 排序）
     */
    public List<SubtaskResponse> list(Long todoId, Long userId) {
        // 先校验用户是否有权访问父任务
        todoService.getAndAssertAccess(todoId, userId, false);

        List<Subtask> subtasks = subtaskMapper.selectList(
                new LambdaQueryWrapper<Subtask>()
                        .eq(Subtask::getTodoId, todoId)
                        .orderByAsc(Subtask::getSortOrder));
        return subtasks.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public synchronized SubtaskResponse create(Long todoId, Long userId, SubtaskRequest req) {
        Todo todo = todoService.getAndAssertAccess(todoId, userId, false);

        Subtask subtask = new Subtask();
        subtask.setTodoId(todoId);
        subtask.setText(req.getText());
        subtask.setCompleted(false);
        subtask.setAssigneeId(req.getAssigneeId());
        if (req.getDueDate() != null && !req.getDueDate().isEmpty()) {
            subtask.setDueDate(LocalDate.parse(req.getDueDate()));
        }
        // sort_order 取当前最大值 + 1
        Integer maxSort = subtaskMapper.selectList(new LambdaQueryWrapper<Subtask>()
                        .eq(Subtask::getTodoId, todoId)
                        .orderByDesc(Subtask::getSortOrder)
                        .last("LIMIT 1"))
                .stream().findFirst().map(Subtask::getSortOrder).orElse(-1);
        subtask.setSortOrder(maxSort + 1);

        subtaskMapper.insert(subtask);
        return toResponse(subtask);
    }

    public SubtaskResponse update(Long subtaskId, Long userId, SubtaskRequest req) {
        Subtask subtask = assertSubtaskAccess(subtaskId, userId);
        subtask.setText(req.getText());
        subtask.setAssigneeId(req.getAssigneeId());
        if (req.getDueDate() != null && !req.getDueDate().isEmpty()) {
            subtask.setDueDate(LocalDate.parse(req.getDueDate()));
        } else {
            subtask.setDueDate(null);
        }
        subtaskMapper.updateById(subtask);
        return toResponse(subtask);
    }

    public SubtaskResponse toggleComplete(Long subtaskId, Long userId) {
        Subtask subtask = assertSubtaskAccess(subtaskId, userId);
        subtask.setCompleted(!subtask.getCompleted());
        subtaskMapper.updateById(subtask);
        return toResponse(subtask);
    }

    public void delete(Long subtaskId, Long userId) {
        Subtask subtask = assertSubtaskAccess(subtaskId, userId);
        subtaskMapper.deleteById(subtaskId);
    }

    @Transactional
    public synchronized void reorder(Long todoId, Long userId, List<ReorderItem> items) {
        todoService.getAndAssertAccess(todoId, userId, false);
        List<Long> ids = items.stream().map(ReorderItem::getId).collect(java.util.stream.Collectors.toList());
        java.util.Map<Long, Subtask> subtaskMap = subtaskMapper.selectBatchIds(ids).stream()
                .filter(s -> s != null && s.getTodoId().equals(todoId))
                .collect(java.util.stream.Collectors.toMap(
                        com.todo.entity.Subtask::getId, s -> s));
        for (ReorderItem item : items) {
            if (subtaskMap.containsKey(item.getId())) {
                subtaskMapper.updateSortOrder(item.getId(), item.getSortOrder());
            }
        }
    }

    /**
     * 校验子任务访问权限：根据父任务的 todo 权限
     */
    private Subtask assertSubtaskAccess(Long subtaskId, Long userId) {
        Subtask subtask = subtaskMapper.selectById(subtaskId);
        if (subtask == null) throw AppException.notFound("子任务");
        // 检查父任务权限
        todoService.getAndAssertAccess(subtask.getTodoId(), userId, false);
        return subtask;
    }

    private SubtaskResponse toResponse(Subtask s) {
        SubtaskResponse r = new SubtaskResponse();
        r.setId(s.getId());
        r.setTodoId(s.getTodoId());
        r.setText(s.getText());
        r.setCompleted(s.getCompleted());
        r.setSortOrder(s.getSortOrder());
        r.setAssigneeId(s.getAssigneeId());
        r.setDueDate(s.getDueDate());
        r.setCreateTime(s.getCreateTime());
        r.setUpdateTime(s.getUpdateTime());
        if (s.getAssigneeId() != null) {
            User assignee = userMapper.selectById(s.getAssigneeId());
            r.setAssigneeName(assignee != null ? assignee.getDisplayName() : null);
        }
        return r;
    }
}