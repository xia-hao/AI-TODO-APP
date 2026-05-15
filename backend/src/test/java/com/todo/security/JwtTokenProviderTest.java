package com.todo.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-that-is-at-least-256-bits-long-for-hs256-algorithm";
    private static final long ACCESS_EXPIRY = 900000L;
    private static final long REFRESH_EXPIRY = 604800000L;
    private static final long SCOPED_EXPIRY = 300000L;

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET, ACCESS_EXPIRY, REFRESH_EXPIRY, SCOPED_EXPIRY);
    }

    @Test
    void generateAccessToken_shouldReturnValidToken() {
        String token = jwtTokenProvider.generateAccessToken(1L, "testuser");
        assertNotNull(token);
        assertTrue(jwtTokenProvider.validate(token));
    }

    @Test
    void getUserId_shouldReturnCorrectId() {
        String token = jwtTokenProvider.generateAccessToken(42L, "testuser");
        assertEquals(42L, jwtTokenProvider.getUserId(token));
    }

    @Test
    void getUserName_shouldReturnCorrectUsername() {
        String token = jwtTokenProvider.generateAccessToken(1L, "testuser");
        assertEquals("testuser", jwtTokenProvider.getUserName(token));
    }

    @Test
    void validate_shouldRejectExpiredToken() throws InterruptedException {
        JwtTokenProvider shortLived = new JwtTokenProvider(SECRET, 1L, 1L, 1L);
        String token = shortLived.generateAccessToken(1L, "testuser");
        Thread.sleep(5);
        assertFalse(shortLived.validate(token));
    }

    @Test
    void validate_shouldRejectTamperedToken() {
        String token = jwtTokenProvider.generateAccessToken(1L, "testuser") + "tampered";
        assertFalse(jwtTokenProvider.validate(token));
    }

    @Test
    void validate_shouldRejectInvalidToken() {
        assertFalse(jwtTokenProvider.validate("invalid.jwt.token"));
    }

    @Test
    void getTokenType_shouldReturnAccess() {
        String token = jwtTokenProvider.generateAccessToken(1L, "testuser");
        assertEquals("access", jwtTokenProvider.getTokenType(token));
    }

    @Test
    void getTokenType_shouldReturnRefresh() {
        String token = jwtTokenProvider.generateRefreshToken(1L, "testuser");
        assertEquals("refresh", jwtTokenProvider.getTokenType(token));
    }

    @Test
    void generateScopedToken_shouldReturnScopedType() {
        String token = jwtTokenProvider.generateScopedToken(1L, "testuser");
        assertEquals("scoped", jwtTokenProvider.getTokenType(token));
    }

    @Test
    void isRefreshToken_shouldReturnTrueForRefreshToken() {
        String token = jwtTokenProvider.generateRefreshToken(1L, "testuser");
        assertTrue(jwtTokenProvider.isRefreshToken(token));
    }

    @Test
    void isRefreshToken_shouldReturnFalseForAccessToken() {
        String token = jwtTokenProvider.generateAccessToken(1L, "testuser");
        assertFalse(jwtTokenProvider.isRefreshToken(token));
    }
}
