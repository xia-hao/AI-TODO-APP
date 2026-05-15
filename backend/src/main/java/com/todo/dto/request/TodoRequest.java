package com.todo.dto.request;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDate;

@Data
public class TodoRequest {
    /** 任务内容 */
    @NotBlank
    @Size(max = 500)
    private String text;

    /** 分类：工作、生活、学习、其他 */
    @NotBlank(message = "分类不能为空")
    private String category = "其他";

    /** 优先级：high、medium、low */
    @NotBlank(message = "优先级不能为空")
    private String priority = "medium";

    /** 截止日期 yyyy-MM-dd */
    private LocalDate dueDate;

    /** 所属团队 ID */
    private Long teamId;

    /** 负责人 ID */
    private Long assigneeId;

    /** 项目 ID（创建时必填，更新时可选） */
    private Long projectId;

    /** 分区 ID（创建时可自动解析默认分区） */
    private Long sectionId;
}
