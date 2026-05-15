package com.todo.security;

import com.todo.entity.User;
import com.todo.mapper.UserMapper;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final List<String> SCOPED_ALLOWED_PREFIXES = Arrays.asList(
        "/api/todos", "/api/projects", "/api/dashboard",
        "/api/teams", "/api/reports", "/api/ai"
    );

    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (StringUtils.hasText(token) && jwtTokenProvider.validate(token)) {
            String type = jwtTokenProvider.getTokenType(token);
            if ("access".equals(type)
                || ("scoped".equals(type) && isToolPath(request.getRequestURI()))) {
                Long userId = jwtTokenProvider.getUserId(token);
                User user = userMapper.selectById(userId);
                if (user != null) {
                    UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }
        chain.doFilter(request, response);
    }

    private boolean isToolPath(String uri) {
        return SCOPED_ALLOWED_PREFIXES.stream().anyMatch(uri::startsWith);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
