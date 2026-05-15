package com.todo.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessExpiry;
    private final long refreshExpiry;
    private final long scopedTokenExpiry;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiry}") long accessExpiry,
            @Value("${app.jwt.refresh-token-expiry}") long refreshExpiry,
            @Value("${app.jwt.scoped-token-expiry}") long scopedTokenExpiry) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpiry = accessExpiry;
        this.refreshExpiry = refreshExpiry;
        this.scopedTokenExpiry = scopedTokenExpiry;
    }

    /** 生成 Access Token（expires at now + accessExpiry） */
    public String generateAccessToken(Long userId, String username) {
        return buildToken(userId, username, accessExpiry, "access");
    }

    /** 生成作用域令牌（expires at now + scopedTokenExpiry） */
    public String generateScopedToken(Long userId, String username) {
        return buildToken(userId, username, scopedTokenExpiry, "scoped");
    }

    /** 生成 Refresh Token（expires at now + refreshExpiry） */
    public String generateRefreshToken(Long userId, String username) {
        return buildToken(userId, username, refreshExpiry, "refresh");
    }

    /**
     * 构建 JWT
     * @param userId   用户 ID
     * @param username 用户名（存入 claims）
     * @param expiry   有效期（毫秒）
     * @param type     token 类型（access / refresh）
     */
    private String buildToken(Long userId, String username, long expiry, String type) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)   // ✅ 写入 username
                .claim("type", type)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiry))
                .signWith(key)
                .compact();
    }

    /** 从 token 解析出 userId */
    public Long getUserId(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    /** 从 token 解析出 username */
    public String getUserName(String token) {
        return parseClaims(token).get("username", String.class);
    }

    /** 判断是否为 refresh token */
    public boolean isRefreshToken(String token) {
        return "refresh".equals(parseClaims(token).get("type", String.class));
    }

    /** 获取 token 类型（access / refresh / scoped） */
    public String getTokenType(String token) {
        return parseClaims(token).get("type", String.class);
    }

    /** 验证 token 有效性 */
    public boolean validate(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 解析 JWT claims
     * @throws JwtException 如果 token 无效
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}