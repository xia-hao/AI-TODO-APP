package com.todo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.todo.entity.Todo;
import com.todo.exception.AppException;
import com.todo.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Lazy;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

    @Mock private TodoMapper todoMapper;
    @Mock private TeamMemberMapper teamMemberMapper;
    @Mock private UserMapper userMapper;
    @Mock private ProjectMapper projectMapper;
    @Mock private SectionMapper sectionMapper;
    @Mock private TagService tagService;
    @Mock private ActivityLogService activityLogService;

    @InjectMocks
    private TodoService todoService;

    private Todo createTodo(Long id, Long ownerId, Long projectId) {
        Todo todo = new Todo();
        todo.setId(id);
        todo.setText("测试待办");
        todo.setOwnerId(ownerId);
        todo.setProjectId(projectId);
        todo.setCompleted(false);
        return todo;
    }

    @Test
    void search_shouldReturnEmptyForBlankKeyword() {
        assertTrue(todoService.search(1L, "").isEmpty());
        assertTrue(todoService.search(1L, null).isEmpty());
        assertTrue(todoService.search(1L, "  ").isEmpty());
    }

    @Test
    void restore_shouldThrowWhenTodoNotDeleted() {
        assertThrows(AppException.class, () -> todoService.restore(1L, 1L));
    }

    @Test
    void permanentlyDelete_shouldThrowWhenTodoNotDeleted() {
        assertThrows(AppException.class, () -> todoService.permanentlyDelete(1L, 1L));
    }

    @Test
    void restore_shouldSucceedForDeletedTodo() {
        Todo todo = createTodo(1L, 1L, null);
        todo.setDeletedTime(LocalDateTime.now());
        when(todoMapper.selectByIdIgnoreLogic(1L)).thenReturn(todo);

        todoService.restore(1L, 1L);

        verify(todoMapper).restoreById(1L);
    }

    @Test
    void permanentlyDelete_shouldCallDeleteById() {
        Todo todo = createTodo(1L, 1L, null);
        todo.setDeletedTime(LocalDateTime.now());
        when(todoMapper.selectByIdIgnoreLogic(1L)).thenReturn(todo);

        todoService.permanentlyDelete(1L, 1L);

        verify(todoMapper).forceDelete(1L);
    }
}
