package com.todo.dto.response;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectResponse extends BaseResponse {
    private String name;
    private String description;
    private String color;
    private String icon;
    private Long ownerId;
    private List<Long> teamIds;
    private List<TeamBrief> teams;
    private Boolean isArchived;
    private Integer sortOrder;
    private List<SectionResponse> sections;

    @Data
    public static class TeamBrief {
        private Long id;
        private String name;
    }
}
