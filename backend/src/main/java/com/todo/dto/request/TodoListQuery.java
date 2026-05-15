package com.todo.dto.request;

import lombok.Data;

@Data
public class TodoListQuery {
    private Long userId;
    private Long teamId;

    /** 状态筛选：active=未完成, completed=已完成 */
    private String status;

    /** 分类筛选：工作、生活、学习、其他 */
    private String category;

    /** 搜索关键词 */
    private String q;
}
