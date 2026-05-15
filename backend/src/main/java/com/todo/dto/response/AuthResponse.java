package com.todo.dto.response;

import lombok.Data;

@Data
public class AuthResponse {
    private String accessToken;
    private UserInfo user;

    @Data
    public static class UserInfo {
        private Long id;
        private String username;
        private String email;
        private String displayName;
    }
}
