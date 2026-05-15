package com.todo.dto.response;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class TeamResponse extends BaseResponse {
    private String name;
    private String description;
    private String inviteCode;
    private Long ownerId;
    private List<MemberInfo> members;
    private String myRole;

    @Data
    public static class MemberInfo {
        private Long userId;
        private String username;
        private String displayName;
        private String role;
        private LocalDateTime joinedAt;
    }
}
