package com.todo.security;

import com.todo.entity.User;
import com.todo.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private UserMapper userMapper;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain chain;

    @InjectMocks
    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldContinueChainWhenNoAuthHeader() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldSetAuthenticationForValidAccessToken() throws ServletException, IOException {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");

        when(request.getHeader("Authorization")).thenReturn("Bearer valid-access-token");
        when(jwtTokenProvider.validate("valid-access-token")).thenReturn(true);
        when(jwtTokenProvider.getTokenType("valid-access-token")).thenReturn("access");
        when(jwtTokenProvider.getUserId("valid-access-token")).thenReturn(1L);
        when(userMapper.selectById(1L)).thenReturn(user);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(user, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    void shouldSetAuthenticationForScopedTokenOnToolPath() throws ServletException, IOException {
        User user = new User();
        user.setId(2L);

        when(request.getHeader("Authorization")).thenReturn("Bearer scoped-token");
        when(request.getRequestURI()).thenReturn("/api/todos/123");
        when(jwtTokenProvider.validate("scoped-token")).thenReturn(true);
        when(jwtTokenProvider.getTokenType("scoped-token")).thenReturn("scoped");
        when(jwtTokenProvider.getUserId("scoped-token")).thenReturn(2L);
        when(userMapper.selectById(2L)).thenReturn(user);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldNotSetAuthenticationForScopedTokenOnNonToolPath() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn("Bearer scoped-token");
        when(request.getRequestURI()).thenReturn("/api/auth/login");
        when(jwtTokenProvider.validate("scoped-token")).thenReturn(true);
        when(jwtTokenProvider.getTokenType("scoped-token")).thenReturn("scoped");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldNotSetAuthenticationForInvalidToken() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid-token");
        when(jwtTokenProvider.validate("invalid-token")).thenReturn(false);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
