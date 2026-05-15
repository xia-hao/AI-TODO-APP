package com.todo.controller;

import com.todo.dto.request.LoginRequest;
import com.todo.dto.request.RegisterRequest;
import com.todo.dto.response.ApiResponse;
import com.todo.dto.response.AuthResponse;
import com.todo.entity.User;
import com.todo.exception.AppException;
import com.todo.service.AuthService;
import com.todo.service.RateLimiterService;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RateLimiterService rateLimiter;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest req,
                                               HttpServletResponse response,
                                               HttpServletRequest request) {
        String key = request.getRemoteAddr() + ":" + req.getEmail();
        if (rateLimiter.isBlocked(key)) throw new AppException("操作过于频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS);
        try {
            ApiResponse<AuthResponse> res = ApiResponse.ok(authService.register(req, response), "注册成功");
            rateLimiter.clear(key);
            return res;
        } catch (AppException e) {
            rateLimiter.recordFailure(key);
            throw e;
        }
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest req,
                                            HttpServletResponse response,
                                            HttpServletRequest request) {
        String key = request.getRemoteAddr() + ":" + req.getEmail();
        if (rateLimiter.isBlocked(key)) throw new AppException("操作过于频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS);
        try {
            ApiResponse<AuthResponse> res = ApiResponse.ok(authService.login(req, response));
            rateLimiter.clear(key);
            return res;
        } catch (AppException e) {
            rateLimiter.recordFailure(key);
            throw e;
        }
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = null;
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if ("refreshToken".equals(c.getName())) { refreshToken = c.getValue(); break; }
            }
        }
        if (refreshToken == null) throw new AppException("缺少刷新令牌", HttpStatus.UNAUTHORIZED);
        return ApiResponse.ok(authService.refresh(refreshToken, response));
    }

    @GetMapping("/me")
    public ApiResponse<AuthResponse.UserInfo> me(@AuthenticationPrincipal User user) {
        AuthResponse.UserInfo info = new AuthResponse.UserInfo();
        info.setId(user.getId());
        info.setUsername(user.getUsername());
        info.setEmail(user.getEmail());
        info.setDisplayName(user.getDisplayName());
        return ApiResponse.ok(info);
    }
}
