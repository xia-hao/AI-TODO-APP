package com.todo.service;

import com.todo.entity.Project;
import com.todo.entity.Todo;
import com.todo.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private TodoMapper todoMapper;
    @Mock private ProjectMapper projectMapper;
    @Mock private ProjectTeamMapper projectTeamMapper;
    @Mock private TeamMemberMapper teamMemberMapper;
    @Mock private TodoTagMapper todoTagMapper;
    @Mock private TagMapper tagMapper;

    @InjectMocks
    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        when(teamMemberMapper.selectList(any())).thenReturn(Collections.emptyList());
    }

    @Test
    void overview_shouldReturnZeroStatsForEmptyTodos() {
        when(projectMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> stats = dashboardService.overview(1L);

        assertEquals(0L, stats.get("total"));
        assertEquals(0L, stats.get("completed"));
        assertEquals(0L, stats.get("active"));
        assertEquals(0L, stats.get("overdue"));
    }

    @Test
    void overview_shouldCountCompleted() {
        Project p = new Project();
        p.setId(1L);
        when(projectMapper.selectList(any())).thenReturn(Arrays.asList(p));
        when(todoMapper.selectCount(any())).thenReturn(2L, 1L, 0L);

        Map<String, Object> stats = dashboardService.overview(1L);

        assertEquals(2L, stats.get("total"));
        assertEquals(1L, stats.get("completed"));
        assertEquals(1L, stats.get("active"));
    }

    @Test
    void projectStats_shouldReturnEmptyForNoProjects() {
        when(projectMapper.selectList(any())).thenReturn(Collections.emptyList());

        assertTrue(dashboardService.projectStats(1L).isEmpty());
    }
}
