package com.todo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.todo.dto.request.LoginRequest;
import com.todo.dto.request.RegisterRequest;
import com.todo.dto.response.AuthResponse;
import com.todo.entity.User;
import com.todo.exception.AppException;
import com.todo.mapper.UserMapper;
import com.todo.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthResponse register(RegisterRequest req, HttpServletResponse response) {
        if (userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, req.getEmail())) != null) {
            throw new AppException("邮箱已被注册", HttpStatus.BAD_REQUEST);
        }
        if (userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername())) != null) {
            throw new AppException("用户名已被使用", HttpStatus.BAD_REQUEST);
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setEmail(req.getEmail());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setDisplayName(req.getDisplayName() != null ? req.getDisplayName() : req.getUsername());
        userMapper.insert(user);
        return buildAuthResponse(user, response);
    }

    public AuthResponse login(LoginRequest req, HttpServletResponse response) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, req.getEmail()));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new AppException("邮箱或密码错误", HttpStatus.UNAUTHORIZED);
        }
        return buildAuthResponse(user, response);
    }

    public AuthResponse refresh(String refreshToken, HttpServletResponse response) {
        if (!jwtTokenProvider.validate(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new AppException("无效的刷新令牌", HttpStatus.UNAUTHORIZED);
        }
        Long userId = jwtTokenProvider.getUserId(refreshToken);
        User user = userMapper.selectById(userId);
        if (user == null) throw new AppException("用户不存在", HttpStatus.UNAUTHORIZED);
        return buildAuthResponse(user, response);
    }

    private AuthResponse buildAuthResponse(User user, HttpServletResponse response) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(),user.getUsername());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(),user.getUsername());

        Cookie cookie = new Cookie("refreshToken", refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/api/auth/refresh");
        cookie.setMaxAge(7 * 24 * 3600);
        response.addCookie(cookie);
        response.setHeader("Set-Cookie", "refreshToken=" + refreshToken
                + "; HttpOnly; Secure; Path=/api/auth/refresh; Max-Age=" + (7 * 24 * 3600)
                + "; SameSite=Strict");

        AuthResponse.UserInfo info = new AuthResponse.UserInfo();
        info.setId(user.getId());
        info.setUsername(user.getUsername());
        info.setEmail(user.getEmail());
        info.setDisplayName(user.getDisplayName());

        AuthResponse res = new AuthResponse();
        res.setAccessToken(accessToken);
        res.setUser(info);
        return res;
    }
}
