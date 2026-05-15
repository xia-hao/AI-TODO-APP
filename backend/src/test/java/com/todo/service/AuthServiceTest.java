package com.todo.service;

import com.todo.dto.request.LoginRequest;
import com.todo.dto.request.RegisterRequest;
import com.todo.dto.response.AuthResponse;
import com.todo.entity.User;
import com.todo.exception.AppException;
import com.todo.mapper.UserMapper;
import com.todo.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private HttpServletResponse response;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setUsername("testuser");
        registerRequest.setEmail("test@example.com");
        registerRequest.setPassword("password123");
        registerRequest.setDisplayName("Test User");

        loginRequest = new LoginRequest();
        loginRequest.setEmail("test@example.com");
        loginRequest.setPassword("password123");
    }

    @Test
    void register_shouldCreateUserAndReturnTokens() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode("password123")).thenReturn("encodedPass");
        doAnswer(inv -> { ((User) inv.getArgument(0)).setId(1L); return 1; })
                .when(userMapper).insert(any(User.class));
        when(jwtTokenProvider.generateAccessToken(any(), anyString())).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(any(), anyString())).thenReturn("refresh-token");

        AuthResponse result = authService.register(registerRequest, response);

        assertNotNull(result);
        assertEquals("access-token", result.getAccessToken());
        assertNotNull(result.getUser());
        assertEquals("testuser", result.getUser().getUsername());
        assertEquals("test@example.com", result.getUser().getEmail());
        assertEquals("Test User", result.getUser().getDisplayName());
    }

    @Test
    void register_shouldThrowWhenEmailAlreadyExists() {
        when(userMapper.selectOne(any())).thenReturn(new User());

        AppException ex = assertThrows(AppException.class,
                () -> authService.register(registerRequest, response));
        assertEquals("邮箱已被注册", ex.getMessage());
    }

    @Test
    void register_shouldUseUsernameAsDisplayNameWhenNull() {
        registerRequest.setDisplayName(null);
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode("password123")).thenReturn("encodedPass");
        doAnswer(inv -> { ((User) inv.getArgument(0)).setId(1L); return 1; })
                .when(userMapper).insert(any(User.class));
        when(jwtTokenProvider.generateAccessToken(any(), anyString())).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(any(), anyString())).thenReturn("refresh-token");

        AuthResponse result = authService.register(registerRequest, response);

        assertEquals("testuser", result.getUser().getDisplayName());
    }

    @Test
    void login_shouldSucceedWithCorrectPassword() {
        User user = createUser();
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("password123", "encodedPass")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(1L, "testuser")).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(1L, "testuser")).thenReturn("refresh-token");

        AuthResponse result = authService.login(loginRequest, response);

        assertNotNull(result);
        assertEquals("access-token", result.getAccessToken());
        assertEquals("testuser", result.getUser().getUsername());
    }

    @Test
    void login_shouldThrowWhenUserNotFound() {
        when(userMapper.selectOne(any())).thenReturn(null);

        AppException ex = assertThrows(AppException.class,
                () -> authService.login(loginRequest, response));
        assertEquals("邮箱或密码错误", ex.getMessage());
    }

    @Test
    void login_shouldThrowWhenPasswordWrong() {
        User user = createUser();
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("password123", "encodedPass")).thenReturn(false);

        AppException ex = assertThrows(AppException.class,
                () -> authService.login(loginRequest, response));
        assertEquals("邮箱或密码错误", ex.getMessage());
    }

    @Test
    void refresh_shouldReturnNewTokensForValidRefreshToken() {
        User user = createUser();
        when(jwtTokenProvider.validate("valid-refresh")).thenReturn(true);
        when(jwtTokenProvider.isRefreshToken("valid-refresh")).thenReturn(true);
        when(jwtTokenProvider.getUserId("valid-refresh")).thenReturn(1L);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(jwtTokenProvider.generateAccessToken(1L, "testuser")).thenReturn("new-access-token");
        when(jwtTokenProvider.generateRefreshToken(1L, "testuser")).thenReturn("new-refresh-token");

        AuthResponse result = authService.refresh("valid-refresh", response);

        assertNotNull(result);
        assertEquals("new-access-token", result.getAccessToken());
    }

    @Test
    void refresh_shouldThrowWhenTokenInvalid() {
        when(jwtTokenProvider.validate("bad-token")).thenReturn(false);

        AppException ex = assertThrows(AppException.class,
                () -> authService.refresh("bad-token", response));
        assertEquals("无效的刷新令牌", ex.getMessage());
    }

    @Test
    void refresh_shouldThrowWhenTokenIsNotRefreshType() {
        when(jwtTokenProvider.validate("access-token")).thenReturn(true);
        when(jwtTokenProvider.isRefreshToken("access-token")).thenReturn(false);

        AppException ex = assertThrows(AppException.class,
                () -> authService.refresh("access-token", response));
        assertEquals("无效的刷新令牌", ex.getMessage());
    }

    @Test
    void refresh_shouldThrowWhenUserNotFound() {
        when(jwtTokenProvider.validate("valid-refresh")).thenReturn(true);
        when(jwtTokenProvider.isRefreshToken("valid-refresh")).thenReturn(true);
        when(jwtTokenProvider.getUserId("valid-refresh")).thenReturn(999L);
        when(userMapper.selectById(999L)).thenReturn(null);

        AppException ex = assertThrows(AppException.class,
                () -> authService.refresh("valid-refresh", response));
        assertEquals("用户不存在", ex.getMessage());
    }

    private User createUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setPassword("encodedPass");
        user.setDisplayName("Test User");
        return user;
    }
}
