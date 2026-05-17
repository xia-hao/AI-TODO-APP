"""工具定义清单 —— 发送给 LLM 的 JSON Schema 集合。

为什么独立文件：
- 定义是数据/契约，随产品需求增长
- 与 handler 逻辑职责分离，便于 review 和版本对比
- 46 个工具定义（已剔除 list_project_sections 重复项）
"""

TOOL_DEFS = [
    # ==================== 任务 ====================
    {
        "type": "function",
        "function": {
            "name": "create_todo",
            "description": "创建新的任务",
            "parameters": {
                "type": "object",
                "properties": {
                    "title": {"type": "string", "description": "任务标题"},
                    "description": {"type": "string", "description": "任务描述（可选）"},
                    "priority": {
                        "type": "string",
                        "enum": ["high", "medium", "low"],
                        "description": "优先级（可选）",
                    },
                    "category": {
                        "type": "string",
                        "enum": ["工作", "生活", "学习", "其他"],
                        "description": "分类（可选）：工作、生活、学习、其他",
                    },
                    "due_date": {"type": "string", "description": "截止日期 yyyy-MM-dd（可选）"},
                    "project_name": {"type": "string", "description": "所属项目名称"},
                    "section_name": {"type": "string", "description": "分区名称"},
                    "team_name": {"type": "string", "description": "所属团队名称（可选）"},
                    "team_id": {"type": "integer", "description": "所属团队ID（可选，与team_name二选一）"},
                    "assignee_name": {"type": "string", "description": "负责人用户名（可选）"},
                },
                "required": ["title", "project_name", "section_name"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "update_todo",
            "description": "更新任务信息（标题、优先级、截止日期等）",
            "parameters": {
                "type": "object",
                "properties": {
                    "keyword": {"type": "string", "description": "要更新的任务关键词"},
                    "title": {"type": "string", "description": "新标题（可选）"},
                    "priority": {
                        "type": "string",
                        "enum": ["high", "medium", "low"],
                        "description": "新优先级（可选）",
                    },
                    "category": {
                        "type": "string",
                        "enum": ["工作", "生活", "学习", "其他"],
                        "description": "新分类（可选）：工作、生活、学习、其他",
                    },
                    "due_date": {"type": "string", "description": "新截止日期 yyyy-MM-dd 或置空（可选）"},
                    "project_name": {"type": "string", "description": "新项目名称（可选）"},
                    "section_name": {"type": "string", "description": "新分区名称（可选）"},
                    "team_name": {"type": "string", "description": "新团队名称（可选）"},
                    "team_id": {"type": "integer", "description": "新团队ID（可选，与team_name二选一）"},
                    "assignee_name": {"type": "string", "description": "新负责人用户名或清空（可选）"},
                },
                "required": ["keyword"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "complete_todo",
            "description": "切换任务的完成/未完成状态",
            "parameters": {
                "type": "object",
                "properties": {
                    "keyword": {"type": "string", "description": "要操作的任务关键词"},
                },
                "required": ["keyword"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "delete_todo",
            "description": "删除任务（移入回收站）",
            "parameters": {
                "type": "object",
                "properties": {
                    "keyword": {"type": "string", "description": "要删除的任务关键词"},
                },
                "required": ["keyword"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "search_todos",
            "description": "搜索/查询任务列表，支持按关键词、状态、分类、项目、团队过滤",
            "parameters": {
                "type": "object",
                "properties": {
                    "keyword": {"type": "string", "description": "搜索关键词"},
                    "status": {
                        "type": "string",
                        "enum": ["active", "completed"],
                        "description": "状态过滤：active=未完成, completed=已完成",
                    },
                    "category": {
                        "type": "string",
                        "enum": ["工作", "生活", "学习", "其他"],
                        "description": "分类过滤：工作、生活、学习、其他",
                    },
                    "project_name": {"type": "string", "description": "项目名称过滤（可选）"},
                    "team_name": {"type": "string", "description": "团队名称过滤（可选）"},
                },
            },
        },
    },
    # ==================== 项目 ====================
    {
        "type": "function",
        "function": {
            "name": "create_project",
            "description": "创建新项目",
            "parameters": {
                "type": "object",
                "properties": {
                    "name": {"type": "string", "description": "项目名称"},
                    "description": {"type": "string", "description": "项目描述（可选）"},
                    "color": {"type": "string", "description": "颜色代码，如 #409eff（可选）"},
                    "icon": {"type": "string", "description": "图标名称或路径（可选）"},
                    "team_names": {
                        "type": "array",
                        "items": {"type": "string"},
                        "description": "关联的团队名称列表（可选）",
                    },
                },
                "required": ["name"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "update_project",
            "description": "更新项目信息",
            "parameters": {
                "type": "object",
                "properties": {
                    "name": {"type": "string", "description": "项目名称（用于查找）"},
                    "new_name": {"type": "string", "description": "新项目名称（可选）"},
                    "description": {"type": "string", "description": "新项目描述（可选）"},
                    "color": {"type": "string", "description": "新颜色代码（可选）"},
                    "icon": {"type": "string", "description": "新图标名称或路径（可选）"},
                    "team_names": {
                        "type": "array",
                        "items": {"type": "string"},
                        "description": "关联的团队名称列表（可选）",
                    },
                },
                "required": ["name"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "delete_project",
            "description": "删除指定项目",
            "parameters": {
                "type": "object",
                "properties": {
                    "name": {"type": "string", "description": "项目名称"},
                },
                "required": ["name"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "list_projects",
            "description": "获取项目列表",
            "parameters": {
                "type": "object",
                "properties": {},
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "list_sections",
            "description": "获取项目的分区列表",
            "parameters": {
                "type": "object",
                "properties": {
                    "project_name": {"type": "string", "description": "项目名称"},
                },
                "required": ["project_name"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "create_section",
            "description": "在项目中创建新分区",
            "parameters": {
                "type": "object",
                "properties": {
                    "project_name": {"type": "string", "description": "所属项目名称"},
                    "name": {"type": "string", "description": "分区名称"},
                },
                "required": ["project_name", "name"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "update_section",
            "description": "更新分区名称",
            "parameters": {
                "type": "object",
                "properties": {
                    "project_name": {"type": "string", "description": "所属项目名称"},
                    "name": {"type": "string", "description": "当前分区名称"},
                    "new_name": {"type": "string", "description": "新分区名称"},
                },
                "required": ["project_name", "name", "new_name"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "delete_section",
            "description": "删除分区",
            "parameters": {
                "type": "object",
                "properties": {
                    "project_name": {"type": "string", "description": "所属项目名称"},
                    "name": {"type": "string", "description": "要删除的分区名称"},
                },
                "required": ["project_name", "name"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "reorder_sections",
            "description": "调整分区排序",
            "parameters": {
                "type": "object",
                "properties": {
                    "project_name": {"type": "string", "description": "项目名称"},
                    "ordered_ids": {
                        "type": "array",
                        "items": {"type": "integer"},
                        "description": "排序后的分区ID列表（从头到尾的顺序）",
                    },
                },
                "required": ["project_name", "ordered_ids"],
            },
        },
    },
    # ==================== 团队 ====================
    {
        "type": "function",
        "function": {
            "name": "create_team",
            "description": "创建新团队",
            "parameters": {
                "type": "object",
                "properties": {
                    "name": {"type": "string", "description": "团队名称"},
                    "description": {"type": "string", "description": "团队描述（可选）"},
                },
                "required": ["name"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "update_team",
            "description": "更新团队信息",
            "parameters": {
                "type": "object",
                "properties": {
                    "name": {"type": "string", "description": "团队名称（用于查找）"},
                    "new_name": {"type": "string", "description": "新团队名称（可选）"},
                    "description": {"type": "string", "description": "新团队描述（可选）"},
                },
                "required": ["name"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "delete_team",
            "description": "解散指定团队",
            "parameters": {
                "type": "object",
                "properties": {
                    "name": {"type": "string", "description": "团队名称"},
                },
                "required": ["name"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "list_teams",
            "description": "获取用户所在的团队列表",
            "parameters": {
                "type": "object",
                "properties": {},
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "list_team_members",
            "description": "获取团队中的成员列表",
            "parameters": {
                "type": "object",
                "properties": {
                    "team_name": {"type": "string", "description": "团队名称"},
                },
                "required": ["team_name"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "add_team_member",
            "description": "添加团队成员",
            "parameters": {
                "type": "object",
                "properties": {
                    "team_name": {"type": "string", "description": "团队名称"},
                    "username": {"type": "string", "description": "成员用户名"},
                    "role": {
                        "type": "string",
                        "enum": ["ADMIN", "MEMBER"],
                        "description": "角色：ADMIN（管理员）或 MEMBER（成员）",
                    },
                },
                "required": ["team_name", "username", "role"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "remove_team_member",
            "description": "从团队中移除成员",
            "parameters": {
                "type": "object",
                "properties": {
                    "team_name": {"type": "string", "description": "团队名称"},
                    "username": {"type": "string", "description": "要移除的成员用户名"},
                },
                "required": ["team_name", "username"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "join_team",
            "description": "通过邀请码加入团队",
            "parameters": {
                "type": "object",
                "properties": {
                    "invite_code": {"type": "string", "description": "团队邀请码"},
                },
                "required": ["invite_code"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "update_member_role",
            "description": "更新团队成员的角色（管理员/普通成员）",
            "parameters": {
                "type": "object",
                "properties": {
                    "team_name": {"type": "string", "description": "团队名称"},
                    "username": {"type": "string", "description": "成员用户名"},
                    "role": {
                        "type": "string",
                        "enum": ["ADMIN", "MEMBER"],
                        "description": "新角色",
                    },
                },
                "required": ["team_name", "username", "role"],
            },
        },
    },
    # ==================== 子任务 ====================
    {
        "type": "function",
        "function": {
            "name": "create_subtask",
            "description": "为某个任务创建子任务",
            "parameters": {
                "type": "object",
                "properties": {
                    "todo_keyword": {"type": "string", "description": "父任务关键词"},
                    "title": {"type": "string", "description": "子任务标题"},
                    "due_date": {"type": "string", "description": "截止日期 yyyy-MM-dd（可选）"},
                },
                "required": ["todo_keyword", "title"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "update_subtask",
            "description": "更新子任务信息",
            "parameters": {
                "type": "object",
                "properties": {
                    "todo_keyword": {"type": "string", "description": "父任务关键词"},
                    "subtask_keyword": {"type": "string", "description": "要更新的子任务关键词"},
                    "title": {"type": "string", "description": "新标题（可选）"},
                    "due_date": {"type": "string", "description": "新截止日期 yyyy-MM-dd（可选）"},
                },
                "required": ["todo_keyword", "subtask_keyword"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "complete_subtask",
            "description": "切换子任务的完成/未完成状态",
            "parameters": {
                "type": "object",
                "properties": {
                    "todo_keyword": {"type": "string", "description": "父任务关键词"},
                    "subtask_keyword": {"type": "string", "description": "要操作的子任务关键词"},
                },
                "required": ["todo_keyword", "subtask_keyword"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "delete_subtask",
            "description": "删除子任务",
            "parameters": {
                "type": "object",
                "properties": {
                    "todo_keyword": {"type": "string", "description": "父任务关键词"},
                    "subtask_keyword": {"type": "string", "description": "要删除的子任务关键词"},
                },
                "required": ["todo_keyword", "subtask_keyword"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "list_subtasks",
            "description": "列出任务的所有子任务",
            "parameters": {
                "type": "object",
                "properties": {
                    "todo_keyword": {"type": "string", "description": "父任务关键词"},
                },
                "required": ["todo_keyword"],
            },
        },
    },
    # ==================== 统计 ====================
    {
        "type": "function",
        "function": {
            "name": "get_statistics",
            "description": "获取任务统计概览",
            "parameters": {
                "type": "object",
                "properties": {
                    "project_name": {"type": "string", "description": "项目名称（可选，指定则只看该项目）"},
                    "team_name": {"type": "string", "description": "团队名称（可选，指定则只看该团队）"},
                },
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "generate_report",
            "description": "生成任务报告",
            "parameters": {
                "type": "object",
                "properties": {
                    "project_name": {"type": "string", "description": "项目名称（可选，指定只看单个项目）"},
                    "team_name": {"type": "string", "description": "团队名称（可选）"},
                    "date_from": {"type": "string", "description": "开始日期 yyyy-MM-dd（可选）"},
                    "date_to": {"type": "string", "description": "结束日期 yyyy-MM-dd（可选）"},
                },
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_calendar_events",
            "description": "获取日历事件（待办），用于在日历上展示",
            "parameters": {
                "type": "object",
                "properties": {
                    "project_name": {"type": "string", "description": "项目名称（可选，指定只看单个项目）"},
                    "team_name": {"type": "string", "description": "团队名称（可选）"},
                    "date_from": {"type": "string", "description": "开始日期 yyyy-MM-dd"},
                    "date_to": {"type": "string", "description": "结束日期 yyyy-MM-dd"},
                },
                "required": ["date_from", "date_to"],
            },
        },
    },
    # ==================== 标签 ====================
    {
        "type": "function",
        "function": {
            "name": "list_project_tags",
            "description": "获取项目中的标签列表",
            "parameters": {
                "type": "object",
                "properties": {
                    "project_name": {"type": "string", "description": "项目名称"},
                },
                "required": ["project_name"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "create_project_tag",
            "description": "创建项目标签",
            "parameters": {
                "type": "object",
                "properties": {
                    "project_name": {"type": "string", "description": "项目名称"},
                    "name": {"type": "string", "description": "标签名称"},
                    "color": {"type": "string", "description": "颜色代码，如 #409eff（可选）"},
                },
                "required": ["project_name", "name"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "list_team_tags",
            "description": "获取团队标签列表",
            "parameters": {
                "type": "object",
                "properties": {
                    "team_name": {"type": "string", "description": "团队名称"},
                },
                "required": ["team_name"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "create_team_tag",
            "description": "创建团队标签",
            "parameters": {
                "type": "object",
                "properties": {
                    "team_name": {"type": "string", "description": "团队名称"},
                    "name": {"type": "string", "description": "标签名称"},
                    "color": {"type": "string", "description": "颜色代码，如 #409eff（可选）"},
                },
                "required": ["team_name", "name"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "delete_tag",
            "description": "删除标签（根据标签ID）",
            "parameters": {
                "type": "object",
                "properties": {
                    "tag_id": {"type": "integer", "description": "要删除的标签ID"},
                },
                "required": ["tag_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "add_tag_to_todo",
            "description": "为任务添加标签",
            "parameters": {
                "type": "object",
                "properties": {
                    "keyword": {"type": "string", "description": "任务关键词"},
                    "tag_id": {"type": "integer", "description": "标签ID"},
                },
                "required": ["keyword", "tag_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "remove_tag_from_todo",
            "description": "从任务移除标签",
            "parameters": {
                "type": "object",
                "properties": {
                    "keyword": {"type": "string", "description": "任务关键词"},
                    "tag_id": {"type": "integer", "description": "标签ID"},
                },
                "required": ["keyword", "tag_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_todo_tags",
            "description": "获取任务的标签列表",
            "parameters": {
                "type": "object",
                "properties": {
                    "keyword": {"type": "string", "description": "任务关键词"},
                },
                "required": ["keyword"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "delete_team_tag",
            "description": "删除团队标签",
            "parameters": {
                "type": "object",
                "properties": {
                    "team_name": {"type": "string", "description": "团队名称"},
                    "name": {"type": "string", "description": "标签名称"},
                },
                "required": ["team_name", "name"],
            },
        },
    },
    # ==================== 评论 ====================
    {
        "type": "function",
        "function": {
            "name": "list_comments",
            "description": "获取任务的评论列表（树形结构）",
            "parameters": {
                "type": "object",
                "properties": {
                    "keyword": {"type": "string", "description": "任务关键词"},
                },
                "required": ["keyword"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "create_comment",
            "description": "给任务添加评论",
            "parameters": {
                "type": "object",
                "properties": {
                    "keyword": {"type": "string", "description": "任务关键词"},
                    "content": {"type": "string", "description": "评论内容"},
                    "parent_id": {"type": "integer", "description": "回复的评论ID（可选，用于回复某条评论）"},
                },
                "required": ["keyword", "content"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "delete_comment",
            "description": "删除评论",
            "parameters": {
                "type": "object",
                "properties": {
                    "todo_id": {"type": "integer", "description": "评论所属的任务ID"},
                    "comment_id": {"type": "integer", "description": "要删除的评论ID"},
                },
                "required": ["todo_id", "comment_id"],
            },
        },
    },
    # ==================== 辅助 ====================
    {
        "type": "function",
        "function": {
            "name": "query_todos_by_project",
            "description": "按高级条件查询项目的待办清单，支持按分区、状态、分类、标签、日期范围等筛选",
            "parameters": {
                "type": "object",
                "properties": {
                    "project_name": {"type": "string", "description": "项目名称"},
                    "section_name": {"type": "string", "description": "分区名称（可选）"},
                    "status": {
                        "type": "string",
                        "enum": ["active", "completed"],
                        "description": "状态过滤：active=未完成, completed=已完成（可选）",
                    },
                    "category": {
                        "type": "string",
                        "enum": ["工作", "生活", "学习", "其他"],
                        "description": "分类过滤（可选）",
                    },
                    "tag_names": {
                        "type": "array",
                        "items": {"type": "string"},
                        "description": "标签名称列表（可选）",
                    },
                    "date_from": {"type": "string", "description": "截止日期开始 yyyy-MM-dd（可选）"},
                    "date_to": {"type": "string", "description": "截止日期结束 yyyy-MM-dd（可选）"},
                    "priority": {
                        "type": "string",
                        "enum": ["high", "medium", "low"],
                        "description": "优先级筛选（可选）",
                    },
                },
                "required": ["project_name"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "move_todo_section",
            "description": "将待办移动到另一个分区",
            "parameters": {
                "type": "object",
                "properties": {
                    "keyword": {"type": "string", "description": "要移动的任务关键词"},
                    "section_name": {"type": "string", "description": "目标分区名称"},
                },
                "required": ["keyword", "section_name"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_todo",
            "description": "获取单个任务详情",
            "parameters": {
                "type": "object",
                "properties": {
                    "keyword": {"type": "string", "description": "要查询的任务关键词"},
                },
                "required": ["keyword"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "restore_todo",
            "description": "从回收站恢复已删除的任务",
            "parameters": {
                "type": "object",
                "properties": {
                    "keyword": {"type": "string", "description": "要恢复的任务关键词"},
                },
                "required": ["keyword"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "delete_todo_permanent",
            "description": "永久删除任务（不可恢复）",
            "parameters": {
                "type": "object",
                "properties": {
                    "keyword": {"type": "string", "description": "要永久删除的任务关键词"},
                },
                "required": ["keyword"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "list_deleted_todos",
            "description": "列出回收站中的已删除待办",
            "parameters": {
                "type": "object",
                "properties": {},
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_todo_by_id",
            "description": "按ID获取单个任务详情（ai内部使用）",
            "parameters": {
                "type": "object",
                "properties": {
                    "todo_id": {"type": "integer", "description": "任务ID"},
                },
                "required": ["todo_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_project_by_id",
            "description": "按ID获取单个项目详情（ai内部使用）",
            "parameters": {
                "type": "object",
                "properties": {
                    "project_id": {"type": "integer", "description": "项目ID"},
                },
                "required": ["project_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_team_by_id",
            "description": "按ID获取单个团队详情（ai内部使用）",
            "parameters": {
                "type": "object",
                "properties": {
                    "team_id": {"type": "integer", "description": "团队ID"},
                },
                "required": ["team_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "reorder_todos",
            "description": "调整任务的排序顺序",
            "parameters": {
                "type": "object",
                "properties": {
                    "ordered_keywords": {
                        "type": "array",
                        "items": {"type": "string"},
                        "description": "按所需顺序排列的任务关键词列表（从头到尾）",
                    },
                },
                "required": ["ordered_keywords"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "import_todos",
            "description": "批量导入任务到指定项目",
            "parameters": {
                "type": "object",
                "properties": {
                    "project_name": {"type": "string", "description": "目标项目名称"},
                    "tasks": {
                        "type": "array",
                        "items": {
                            "type": "object",
                            "properties": {
                                "title": {"type": "string", "description": "任务标题"},
                                "category": {
                                    "type": "string",
                                    "enum": ["工作", "生活", "学习", "其他"],
                                    "description": "分类（可选）",
                                },
                                "priority": {
                                    "type": "string",
                                    "enum": ["high", "medium", "low"],
                                    "description": "优先级（可选）",
                                },
                                "due_date": {"type": "string", "description": "截止日期 yyyy-MM-dd（可选）"},
                                "completed": {"type": "boolean", "description": "是否已完成（可选）"},
                            },
                            "required": ["title"],
                        },
                        "description": "要导入的任务列表",
                    },
                },
                "required": ["project_name", "tasks"],
            },
        },
    },
]
