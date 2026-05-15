package com.todo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reports")
public class Report extends BaseEntity {
    private Long userId;
    private String type;         // DAILY / WEEKLY
    private String scope;        // SELF / TEAM
    private Long teamId;
    private String title;
    private String preview;
    private String content;      // Markdown 全文
    private String jsonData;     // JSON 结构化数据
    private LocalDate periodStart;
    private LocalDate periodEnd;
}
