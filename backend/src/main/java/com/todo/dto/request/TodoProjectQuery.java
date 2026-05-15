package com.todo.dto.request;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class TodoProjectQuery {
    private Long userId;
    @NotNull
    private Long projectId;
    private Long sectionId;
    private String status;
    private String category;
    private String q;
    private List<Long> tagIds;
    private String dateFrom;
    private String dateTo;
}
