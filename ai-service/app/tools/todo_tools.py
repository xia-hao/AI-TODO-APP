import json
import logging
from functools import partial

import httpx

from app.config import Settings
from app.tools.context import scope_token

logger = logging.getLogger(__name__)

TOOL_DEFS = [
    # ==================== Todo ====================
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
    # ==================== Project ====================
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
            "name": "list_project_sections",
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
    # ==================== Team ====================
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
    # ==================== Subtask ====================
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
    # ==================== Statistics ====================
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
    # ==================== Tag ====================
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
    # ==================== Comment ====================
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
    # ==================== Admin/Helper ====================
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

def register_all(registry, settings: Settings):
    async def _safe_wrapper(handler, **kwargs) -> str:
        try:
            return await handler(**kwargs)
        except Exception as e:
            logger.error("Handler %s failed: %s", handler.__name__, e, exc_info=True)
            return f"操作失败：{e}"

    for tool_def in TOOL_DEFS:
        name = tool_def["function"]["name"]
        handler = _TOOL_HANDLERS[name]
        bound = partial(handler, settings)
        wrapped = partial(_safe_wrapper, bound)
        wrapped.__name__ = name
        registry.register(tool_def, wrapped)
    logger.info("Registered %d todo tools — backend=%s", len(TOOL_DEFS), _base(settings))


# ==================== HTTP Helpers ====================
def _base(settings: Settings) -> str:
    return settings.api_base_url


def _headers(settings: Settings) -> dict:
    h = {"Content-Type": "application/json"}
    token = scope_token.get() or settings.scope_token
    if token:
        prefix = settings.api_auth_token_prefix.rstrip(" ")
        if not token.startswith(prefix + " "):
            token = f"{prefix} {token}"
        h[settings.api_auth_header] = token
    return h


async def _request(method: str, settings: Settings, path: str, json_data: dict = None, params: dict = None) -> dict:
    async with httpx.AsyncClient(timeout=30) as client:
        resp = await client.request(
            method, f"{_base(settings)}{path}",
            json=json_data, params=params,
            headers=_headers(settings),
        )
    # Parse response body before raise_for_status so we can extract error message
    body = None
    try:
        body = resp.json()
    except Exception:
        pass
    try:
        resp.raise_for_status()
    except httpx.HTTPStatusError as e:
        if isinstance(body, dict):
            msg = body.get("message", str(e))
            raise RuntimeError(msg)
        raise
    if body is None:
        raise RuntimeError("服务器返回了非 JSON 响应，请稍后重试")
    if isinstance(body, dict) and body.get("code") in (200, 0):
        return body.get("data", body)
    if isinstance(body, dict):
        raise RuntimeError(body.get("message", f"API 返回错误 (code={body.get('code')})"))
    return body


async def _get(settings: Settings, path: str, params: dict = None) -> dict:
    return await _request("GET", settings, path, params=params)


async def _post(settings: Settings, path: str, json_data: dict = None) -> dict:
    return await _request("POST", settings, path, json_data=json_data)


async def _put(settings: Settings, path: str, json_data: dict = None) -> dict:
    return await _request("PUT", settings, path, json_data=json_data)


async def _patch(settings: Settings, path: str, json_data: dict = None) -> dict:
    return await _request("PATCH", settings, path, json_data=json_data)


async def _delete(settings: Settings, path: str) -> dict:
    return await _request("DELETE", settings, path)


# ==================== Name Resolution ====================

async def _resolve_name_to_id(settings: Settings, name_type: str, name: str) -> int | None:
    if not name:
        return None
    try:
        if name_type == "project":
            data = await _get(settings, "/projects", {"name": name})
            for p in (data if isinstance(data, list) else []):
                if isinstance(p, dict) and p.get("name") == name:
                    return p.get("id")
        elif name_type == "team":
            data = await _get(settings, "/teams", {"name": name})
            for t in (data if isinstance(data, list) else []):
                if isinstance(t, dict) and t.get("name") == name:
                    return t.get("id")
    except Exception as e:
        logger.warning("Failed to resolve %s name '%s': %s", name_type, name, e)
    return None


async def _resolve_assignee_name(
    settings: Settings, assignee_name: str, *,
    team_id: int | None = None, project_id: int | None = None,
) -> list[dict]:
    """Resolve assignee username/displayName to user ID via team members.

    Returns a list of {userId, teamId, teamName} dicts — there may be
    multiple results if the same username exists in several teams.
    Empty list means not found.

    Uses the given team_id first; if not provided, looks up project's
    associated teams and searches across all of them.
    """
    if not assignee_name:
        return []

    team_ids: list[int] = []
    if team_id:
        team_ids = [team_id]
    elif project_id:
        try:
            teams_resp = await _get(settings, f"/projects/{project_id}/teams")
            if isinstance(teams_resp, list):
                team_ids = [t["id"] for t in teams_resp if isinstance(t, dict) and t.get("id")]
        except Exception as e:
            logger.warning("Failed to get teams for project %s: %s", project_id, e)

    if not team_ids:
        return []

    results: list[dict] = []
    for tid in team_ids:
        try:
            data = await _get(settings, f"/teams/{tid}")
            if isinstance(data, dict):
                team_name = data.get("name", f"ID:{tid}")
                members = data.get("members", [])
                for m in members:
                    if isinstance(m, dict):
                        if m.get("username") == assignee_name or m.get("displayName") == assignee_name:
                            results.append({
                                "userId": m.get("userId"),
                                "teamId": tid,
                                "teamName": team_name,
                            })
        except Exception as e:
            logger.warning("Failed to resolve assignee in team %s: %s", tid, e)
    return results


async def _resolve_section_name(settings: Settings, project_id: int | None, section_name: str) -> int | None:
    if not section_name or not project_id:
        return None
    try:
        data = await _get(settings, f"/projects/{project_id}/sections")
        for s in (data if isinstance(data, list) else []):
            if isinstance(s, dict) and s.get("name") == section_name:
                return s.get("id")
    except Exception as e:
        logger.warning("Failed to resolve section name '%s' for project %s: %s", section_name, project_id, e)
    return None


async def _search_todo_by_keyword(settings: Settings, keyword: str) -> tuple[int | None, str]:
    """Search a single todo by keyword. Returns (todo_id, error_msg)."""
    try:
        result = await _get(settings, "/todos/search", {"q": keyword})
    except Exception as e:
        return None, f"搜索任务失败：{e}"
    todos = result if isinstance(result, list) else []
    if not todos:
        return None, f"未找到匹配的任务：{keyword}"
    if len(todos) > 1:
        names = "\n".join(
            "- " + (t.get("text") or "任务" + str(t.get("id", "")))
            for t in todos[:5]
        )
        return None, f"找到多个匹配的任务，请更精确地指定：\n{names}"
    return todos[0].get("id"), None


async def _search_subtask_by_keyword(settings: Settings, todo_id: int, keyword: str) -> tuple[int | None, str]:
    """Search a single subtask by keyword within a todo."""
    try:
        result = await _get(settings, f"/todos/{todo_id}/subtasks")
    except Exception as e:
        return None, f"查询子任务失败：{e}"
    subtasks = result if isinstance(result, list) else []
    matches = [s for s in subtasks if isinstance(s, dict) and keyword in (s.get("text") or s.get("title", ""))]
    if not matches:
        return None, f"未找到匹配的子任务：{keyword}"
    if len(matches) > 1:
        names = "\n".join(
            "- " + (m.get("text") or "子任务" + str(m.get("id", "")))
            for m in matches[:5]
        )
        return None, f"找到多个匹配的子任务，请更精确地指定：\n{names}"
    return matches[0].get("id"), None


async def _resolve_name_and_section(
    settings: Settings, kwargs: dict, body: dict,
) -> str | None:
    """Resolve project_name, team_name, section_name in kwargs to IDs in body.
    Returns error string if fatal, None on success."""
    if "project_name" in kwargs and kwargs["project_name"]:
        pid = await _resolve_name_to_id(settings, "project", kwargs["project_name"])
        if pid:
            body["projectId"] = pid

    if "team_name" in kwargs and kwargs["team_name"]:
        tid = await _resolve_name_to_id(settings, "team", kwargs["team_name"])
        if tid:
            body["teamId"] = tid

    if "section_name" in kwargs and kwargs["section_name"]:
        project_id = body.get("projectId")
        if project_id:
            sid = await _resolve_section_name(settings, project_id, kwargs["section_name"])
            if sid:
                body["sectionId"] = sid

    return None


# ==================== Todo Handlers ====================

async def _create_todo(settings: Settings, **kwargs) -> str:
    if not kwargs.get("title"):
        return "请提供任务标题。"
    if not kwargs.get("project_name"):
        return "请提供所属项目名称。"
    if not kwargs.get("section_name"):
        return "请提供分区名称。"

    body = {}
    for k, v in kwargs.items():
        if v is None:
            continue
        if k in ("project_name", "section_name", "team_name", "assignee_name"):
            continue
        body[k] = v

    # remap known fields
    field_map = {"title": "text", "due_date": "dueDate", "team_id": "teamId"}
    for old_k, new_k in field_map.items():
        if old_k in body:
            body[new_k] = body.pop(old_k)

    await _resolve_name_and_section(settings, kwargs, body)

    if kwargs.get("assignee_name"):
        matches = await _resolve_assignee_name(
            settings, kwargs["assignee_name"],
            team_id=body.get("teamId"),
            project_id=body.get("projectId"),
        )
        if not matches:
            return f"未找到负责人「{kwargs['assignee_name']}」，请确认用户名是否正确。"
        if len(matches) == 1:
            body["assigneeId"] = matches[0]["userId"]
            body["teamId"] = matches[0]["teamId"]
        else:
            teams_info = "\n".join(
                f"  {m['teamName']}(ID:{m['teamId']})"
                for m in matches
            )
            return f"负责人「{kwargs['assignee_name']}」在多个团队中找到，请指定团队：\n{teams_info}"

    if not body.get("projectId"):
        return f"未找到项目「{kwargs['project_name']}」"
    if not body.get("sectionId"):
        return f"项目中没有找到名为「{kwargs['section_name']}」的分区。"

    data = await _post(settings, "/todos", body)
    return f"任务创建成功！\n{_fmt_response(data)}"


async def _update_todo(settings: Settings, **kwargs) -> str:
    keyword = kwargs.pop("keyword", None)
    if not keyword:
        return "请提供要更新的任务关键词。"

    todo_id, err = await _search_todo_by_keyword(settings, keyword)
    if err:
        return err

    # Fetch existing todo data to fill required fields
    existing = None
    try:
        existing = await _get(settings, f"/todos/{todo_id}")
    except Exception as e:
        return f"获取任务详情失败：{e}"
    if not isinstance(existing, dict):
        return "未找到匹配的任务。"

    # Merge: existing values as defaults, user-provided values override
    body = {
        "text": existing.get("text", ""),
        "category": existing.get("category", "other"),
        "priority": existing.get("priority", "medium"),
        "projectId": existing.get("projectId"),
        "sectionId": existing.get("sectionId"),
        "teamId": existing.get("teamId"),
        "assigneeId": existing.get("assigneeId"),
    }

    if kwargs.get("title"):
        body["text"] = kwargs["title"]
    if kwargs.get("category"):
        body["category"] = kwargs["category"]
    if kwargs.get("priority"):
        body["priority"] = kwargs["priority"]
    if "due_date" in kwargs:
        body["dueDate"] = kwargs["due_date"] if kwargs["due_date"] else None

    if kwargs.get("project_name"):
        pid = await _resolve_name_to_id(settings, "project", kwargs["project_name"])
        if pid:
            body["projectId"] = pid
    if kwargs.get("section_name"):
        sid = await _resolve_section_name(settings, body.get("projectId"), kwargs["section_name"])
        if sid:
            body["sectionId"] = sid

    if kwargs.get("team_name"):
        tid = await _resolve_name_to_id(settings, "team", kwargs["team_name"])
        if tid:
            body["teamId"] = tid

    if "team_id" in kwargs:
        body["teamId"] = kwargs["team_id"]

    if "assignee_name" in kwargs:
        assignee_name = kwargs["assignee_name"]
        if assignee_name:
            matches = await _resolve_assignee_name(
                settings, assignee_name,
                team_id=body.get("teamId"),
                project_id=body.get("projectId"),
            )
            if not matches:
                return f"未找到负责人「{assignee_name}」，请确认用户名是否正确。"
            if len(matches) == 1:
                body["assigneeId"] = matches[0]["userId"]
                body["teamId"] = matches[0]["teamId"]
            else:
                teams_info = "\n".join(
                    f"  {m['teamName']}(ID:{m['teamId']})"
                    for m in matches
                )
                return f"负责人「{assignee_name}」在多个团队中找到，请指定团队：\n{teams_info}"
        else:
            body["assigneeId"] = None

    try:
        data = await _put(settings, f"/todos/{todo_id}", body)
    except Exception as e:
        return f"更新任务失败：{e}"

    return f"任务已更新！\n{_fmt_response(data)}"


async def _complete_todo(settings: Settings, **kwargs) -> str:
    keyword = kwargs.get("keyword")
    if not keyword:
        return "请提供要操作的任务关键词。"

    todo_id, err = await _search_todo_by_keyword(settings, keyword)
    if err:
        return err

    data = await _patch(settings, f"/todos/{todo_id}/complete")
    return f"任务状态已切换！\n{_fmt_response(data)}"


async def _delete_todo(settings: Settings, **kwargs) -> str:
    keyword = kwargs.get("keyword")
    if not keyword:
        return "请提供要删除的任务关键词。"

    todo_id, err = await _search_todo_by_keyword(settings, keyword)
    if err:
        return err

    data = await _delete(settings, f"/todos/{todo_id}")
    return f"任务已移入回收站！\n{_fmt_response(data)}"


async def _restore_todo(settings: Settings, **kwargs) -> str:
    keyword = kwargs.get("keyword")
    if not keyword:
        return "请提供要恢复的任务关键词。"

    data = await _get(settings, "/todos/deleted")
    todos = data if isinstance(data, list) else []
    if not todos:
        return "回收站为空，没有可恢复的任务。"

    keyword_lower = keyword.lower()
    matched = [t for t in todos if keyword_lower in (t.get("text") or "").lower()]

    if not matched:
        return f"回收站中未找到匹配的任务：{keyword}"
    if len(matched) > 1:
        names = "\n".join(
            "- " + (t.get("text") or "任务" + str(t.get("id", "")))
            for t in matched[:5]
        )
        return f"找到多个匹配的已删除任务，请更精确地指定：\n{names}"

    todo_id = matched[0].get("id")
    data = await _patch(settings, f"/todos/{todo_id}/restore")
    return "任务已恢复！"


async def _delete_todo_permanent(settings: Settings, **kwargs) -> str:
    keyword = kwargs.get("keyword")
    if not keyword:
        return "请提供要永久删除的任务关键词。"

    # 先在正常任务中搜索
    todo_id, err = await _search_todo_by_keyword(settings, keyword)
    if not err:
        data = await _delete(settings, f"/todos/{todo_id}/permanent")
        return "任务已永久删除！"

    # 正常任务中没找到，再在回收站中搜索
    data = await _get(settings, "/todos/deleted")
    todos = data if isinstance(data, list) else []
    if not todos:
        return f"未找到匹配的任务：{keyword}"

    keyword_lower = keyword.lower()
    matched = [t for t in todos if keyword_lower in (t.get("text") or "").lower()]

    if not matched:
        return f"未找到匹配的任务：{keyword}"
    if len(matched) > 1:
        names = "\n".join(
            "- " + (t.get("text") or "任务" + str(t.get("id", "")))
            for t in matched[:5]
        )
        return f"找到多个匹配的任务，请更精确地指定：\n{names}"

    todo_id = matched[0].get("id")
    data = await _delete(settings, f"/todos/{todo_id}/permanent")
    return "任务已永久删除！"


async def _list_deleted_todos(settings: Settings, **kwargs) -> str:
    data = await _get(settings, "/todos/deleted")
    items = data if isinstance(data, list) else []
    if not items:
        return "回收站为空。"
    return _fmt_items(items)


async def _search_todos(settings: Settings, **kwargs) -> str:
    params = {}
    for k, v in kwargs.items():
        if v is None:
            continue
        if k == "team_name":
            tid = await _resolve_name_to_id(settings, "team", v)
            if tid:
                params["teamId"] = tid
        elif k == "keyword":
            params["q"] = v
        else:
            params[k] = v

    if "project_name" in params:
        pid = await _resolve_name_to_id(settings, "project", params.pop("project_name"))
        if pid:
            params["projectId"] = pid

    data = await _get(settings, "/todos", params)
    return _format_todo_list(data)


def _format_todo_list(data) -> str:
    items = data if isinstance(data, list) else []
    if not items:
        return "没有找到任何待办事项。"
    lines = [f"共 {len(items)} 条待办："]
    for i, item in enumerate(items[:50], 1):
        text = _escape(item.get("text") or "")
        priority = item.get("priority", "medium")
        completed = item.get("completed", False)
        status = "✅" if completed else "⬜"
        due = item.get("dueDate") or ""
        project = item.get("projectName") or ""
        section = item.get("sectionName") or ""
        assignee = item.get("assigneeName") or ""
        tags = item.get("tags")
        tag_str = ""
        if isinstance(tags, list) and tags:
            tag_str = " [" + ",".join(t.get("name", "") for t in tags if isinstance(t, dict) and t.get("name")) + "]"
        lines.append(f"{i}. {status} {text} [{priority}]{f' 截止:{due}' if due else ''}{f' 项目:{project}' if project else ''}{f' 分区:{section}' if section else ''}{f' 负责人:{assignee}' if assignee else ''}{tag_str}")
    if len(items) > 50:
        lines.append(f"... 还有 {len(items) - 50} 条")
    return "\n".join(lines)


# ==================== Project Handlers ====================
async def _create_project(settings: Settings, **kwargs) -> str:
    if not kwargs.get("name"):
        return "请提供项目名称。"
    body = {}
    for k, v in kwargs.items():
        if v is None:
            continue
        if k == "team_names":
            if isinstance(v, list):
                team_ids = []
                for name in v:
                    tid = await _resolve_name_to_id(settings, "team", name)
                    if tid:
                        team_ids.append(tid)
                if team_ids:
                    body["teamIds"] = team_ids
            continue
        body[k] = v
    data = await _post(settings, "/projects", body)
    return f"项目创建成功！\n{_fmt_response(data)}"


async def _update_project(settings: Settings, **kwargs) -> str:
    name = kwargs.get("name")
    if not name:
        return "请提供项目名称。"
    pid = await _resolve_name_to_id(settings, "project", name)
    if not pid:
        return f"未找到名为「{name}」的项目。"

    body = {}
    if "new_name" in kwargs and kwargs["new_name"]:
        body["name"] = kwargs["new_name"]
    else:
        try:
            existing = await _get(settings, f"/projects/{pid}")
            body["name"] = existing.get("name", name)
        except Exception:
            body["name"] = name
    if "description" in kwargs and kwargs["description"]:
        body["description"] = kwargs["description"]
    if "color" in kwargs and kwargs["color"]:
        body["color"] = kwargs["color"]
    if "icon" in kwargs and kwargs["icon"]:
        body["icon"] = kwargs["icon"]
    if "team_names" in kwargs and kwargs["team_names"]:
        if isinstance(kwargs["team_names"], list):
            team_ids = []
            for name in kwargs["team_names"]:
                tid = await _resolve_name_to_id(settings, "team", name)
                if tid:
                    team_ids.append(tid)
            if team_ids:
                body["teamIds"] = team_ids
    data = await _put(settings, f"/projects/{pid}", body)
    return f"项目已更新！\n{_fmt_response(data)}"


async def _delete_project(settings: Settings, **kwargs) -> str:
    name = kwargs.get("name")
    if not name:
        return "请提供要删除的项目名称。"
    pid = await _resolve_name_to_id(settings, "project", name)
    if not pid:
        return f"未找到名为「{name}」的项目。"
    await _delete(settings, f"/projects/{pid}")
    return f"项目「{name}」已删除。"


async def _list_projects(settings: Settings, **kwargs) -> str:
    data = await _get(settings, "/projects")
    if not isinstance(data, list):
        return f"项目列表：\n{_fmt_response(data)}"
    lines = []
    for idx, proj in enumerate(data, 1):
        name = proj.get("name", f"项目{idx}")
        pid = proj.get("id")
        sections = []
        if pid:
            try:
                sec_data = await _get(settings, f"/projects/{pid}/sections")
                sections = [s.get("name", "") for s in (sec_data if isinstance(sec_data, list) else [])]
            except Exception:
                pass
        parts = [str(idx) + ".", name]
        if sections:
            parts.append(f"（分区：{'、'.join(sections)}）")
        lines.append(" ".join(parts))
    return f"项目列表：\n" + "\n".join(lines) if lines else "（无）"


async def _list_project_sections(settings: Settings, **kwargs) -> str:
    project_name = kwargs.get("project_name")
    if not project_name:
        return "请提供项目名称。"
    pid = await _resolve_name_to_id(settings, "project", project_name)
    if not pid:
        return f"未找到项目「{project_name}」"
    data = await _get(settings, f"/projects/{pid}/sections")
    items = data if isinstance(data, list) else []
    if not items:
        return f"项目「{project_name}」下暂无分区。"
    lines = [f"项目「{project_name}」的分区（共 {len(items)} 个）："]
    for s in items:
        name = s.get("name") or ""
        lines.append(f"- {name}")
    return "\n".join(lines)


async def _create_section(settings: Settings, **kwargs) -> str:
    project_name = kwargs.get("project_name")
    name = kwargs.get("name")
    if not project_name or not name:
        return "请提供项目名称和分区名称。"
    pid = await _resolve_name_to_id(settings, "project", project_name)
    if not pid:
        return f"未找到项目「{project_name}」"
    body = {"name": name, "projectId": pid}
    data = await _post(settings, f"/projects/{pid}/sections", body)
    return f"分区创建成功！\n{_fmt_response(data)}"


async def _update_section(settings: Settings, **kwargs) -> str:
    project_name = kwargs.get("project_name")
    name = kwargs.get("name")
    new_name = kwargs.get("new_name")
    if not project_name or not name or not new_name:
        return "请提供项目名称、当前分区名称和新分区名称。"
    pid = await _resolve_name_to_id(settings, "project", project_name)
    if not pid:
        return f"未找到项目「{project_name}」"
    sid = await _resolve_section_name(settings, pid, name)
    if not sid:
        return f"项目中没有找到名为「{name}」的分区。"
    body = {"name": new_name}
    data = await _put(settings, f"/projects/{pid}/sections/{sid}", body)
    return f"分区已更新！\n{_fmt_response(data)}"


async def _delete_section(settings: Settings, **kwargs) -> str:
    project_name = kwargs.get("project_name")
    name = kwargs.get("name")
    if not project_name or not name:
        return "请提供项目名称和分区名称。"
    pid = await _resolve_name_to_id(settings, "project", project_name)
    if not pid:
        return f"未找到项目「{project_name}」"
    sid = await _resolve_section_name(settings, pid, name)
    if not sid:
        return f"项目中没有找到名为「{name}」的分区。"
    await _delete(settings, f"/projects/{pid}/sections/{sid}")
    return "分区已删除。"


async def _reorder_sections(settings: Settings, **kwargs) -> str:
    project_name = kwargs.get("project_name")
    ordered_ids = kwargs.get("ordered_ids")
    if not project_name or not ordered_ids:
        return "请提供项目名称和排序后的分区ID列表。"
    pid = await _resolve_name_to_id(settings, "project", project_name)
    if not pid:
        return f"未找到项目「{project_name}」"
    await _put(settings, f"/projects/{pid}/sections/reorder", ordered_ids)
    return "分区排序已更新。"


# ==================== Team Handlers ====================
async def _create_team(settings: Settings, **kwargs) -> str:
    if not kwargs.get("name"):
        return "请提供团队名称。"
    body = {k: v for k, v in kwargs.items() if v is not None}
    data = await _post(settings, "/teams", body)
    return f"团队创建成功！\n{_fmt_response(data)}"


async def _update_team(settings: Settings, **kwargs) -> str:
    name = kwargs.get("name")
    if not name:
        return "请提供团队名称。"
    tid = await _resolve_name_to_id(settings, "team", name)
    if not tid:
        return f"未找到名为「{name}」的团队。"
    body = {}
    if "new_name" in kwargs and kwargs["new_name"]:
        body["name"] = kwargs["new_name"]
    else:
        try:
            existing = await _get(settings, f"/teams/{tid}")
            body["name"] = existing.get("name", name)
        except Exception:
            body["name"] = name
    if "description" in kwargs and kwargs["description"]:
        body["description"] = kwargs["description"]
    data = await _put(settings, f"/teams/{tid}", body)
    return f"团队已更新！\n{_fmt_response(data)}"


async def _delete_team(settings: Settings, **kwargs) -> str:
    name = kwargs.get("name")
    if not name:
        return "请提供要解散的团队名称。"
    tid = await _resolve_name_to_id(settings, "team", name)
    if not tid:
        return f"未找到名为「{name}」的团队。"
    await _delete(settings, f"/teams/{tid}")
    return f"团队「{name}」已解散。"


async def _list_teams(settings: Settings, **kwargs) -> str:
    data = await _get(settings, "/teams")
    return f"团队列表：\n{_fmt_response(data)}"


async def _list_team_members(settings: Settings, **kwargs) -> str:
    team_name = kwargs.get("team_name")
    if not team_name:
        return "请提供团队名称。"
    tid = await _resolve_name_to_id(settings, "team", team_name)
    if not tid:
        return f"未找到团队「{team_name}」。"
    data = await _get(settings, f"/teams/{tid}")
    members = data.get("members", []) if isinstance(data, dict) else []
    if not members:
        return f"团队「{team_name}」暂无成员。"
    lines = [f"团队「{team_name}」成员列表："]
    for idx, m in enumerate(members, 1):
        uid = m.get("userId", "")
        name = m.get("displayName") or m.get("username", "")
        role = m.get("role", "")
        lines.append(f"  {idx}. {name}（{role}）")
    return "\n".join(lines)


async def _add_team_member(settings: Settings, **kwargs) -> str:
    team_name = kwargs.get("team_name")
    username = kwargs.get("username")
    role = kwargs.get("role")
    if not team_name or not username or not role:
        return "请提供团队名称、成员用户名和角色。"

    tid = await _resolve_name_to_id(settings, "team", team_name)
    if not tid:
        return f"未找到团队「{team_name}」"

    body = {"username": username, "role": role}
    data = await _post(settings, f"/teams/{tid}/members", body)
    return f"成员添加成功！\n{_fmt_response(data)}"


async def _remove_team_member(settings: Settings, **kwargs) -> str:
    team_name = kwargs.get("team_name")
    username = kwargs.get("username")
    if not team_name or not username:
        return "请提供团队名称和成员用户名。"
    tid = await _resolve_name_to_id(settings, "team", team_name)
    if not tid:
        return f"未找到团队「{team_name}」"
    data = await _get(settings, f"/teams/{tid}")
    members = data.get("members", []) if isinstance(data, dict) else []
    uid = None
    for m in members:
        if isinstance(m, dict) and m.get("username") == username:
            uid = m.get("userId")
            break
    if not uid:
        return f"未在团队中找到成员「{username}」。"
    await _delete(settings, f"/teams/{tid}/members/{uid}")
    return f"成员「{username}」已从团队移除。"


async def _join_team(settings: Settings, **kwargs) -> str:
    invite_code = kwargs.get("invite_code")
    if not invite_code:
        return "请提供邀请码。"
    data = await _post(settings, "/teams/join", {"inviteCode": invite_code})
    return f"加入团队成功！\n{_fmt_response(data)}"


async def _update_member_role(settings: Settings, **kwargs) -> str:
    team_name = kwargs.get("team_name")
    username = kwargs.get("username")
    role = kwargs.get("role")
    if not team_name or not username or not role:
        return "请提供团队名称、成员用户名和角色。"
    tid = await _resolve_name_to_id(settings, "team", team_name)
    if not tid:
        return f"未找到团队「{team_name}」"
    data = await _get(settings, f"/teams/{tid}")
    members = data.get("members", []) if isinstance(data, dict) else []
    user_id = None
    for m in members:
        if m.get("username") == username or m.get("displayName") == username:
            user_id = m.get("userId")
            break
    if not user_id:
        return f"未在团队「{team_name}」中找到成员「{username}」"
    await _put(settings, f"/teams/{tid}/members/{user_id}/role", {"role": role})
    return f"成员「{username}」的角色已更新为 {role}。"


# ==================== Subtask Handlers ====================
async def _create_subtask(settings: Settings, **kwargs) -> str:
    todo_keyword = kwargs.get("todo_keyword")
    title = kwargs.get("title")
    if not todo_keyword or not title:
        return "请提供父任务关键词和子任务标题。"

    todo_id, err = await _search_todo_by_keyword(settings, todo_keyword)
    if err:
        return err

    body = {}
    if title:
        body["text"] = title
    due_date = kwargs.get("due_date")
    if due_date:
        body["dueDate"] = due_date

    data = await _post(settings, f"/todos/{todo_id}/subtasks", body)
    return f"子任务创建成功！\n{_fmt_response(data)}"


async def _update_subtask(settings: Settings, **kwargs) -> str:
    todo_keyword = kwargs.get("todo_keyword")
    subtask_keyword = kwargs.get("subtask_keyword")
    if not todo_keyword or not subtask_keyword:
        return "请提供父任务关键词和子任务关键词。"

    todo_id, err = await _search_todo_by_keyword(settings, todo_keyword)
    if err:
        return err

    subtask_id, err = await _search_subtask_by_keyword(settings, todo_id, subtask_keyword)
    if err:
        return err

    body = {}
    title_provided = "title" in kwargs and kwargs["title"]
    if title_provided:
        body["text"] = kwargs["title"]
    else:
        try:
            result = await _get(settings, f"/todos/{todo_id}/subtasks")
            subtasks = result if isinstance(result, list) else []
            existing = next((s for s in subtasks if s.get("id") == subtask_id), None)
            if existing and existing.get("text"):
                body["text"] = existing["text"]
        except Exception:
            pass
    if "text" not in body:
        return "无法更新子任务：缺少标题且无法获取当前子任务信息。"
    if "due_date" in kwargs:
        body["dueDate"] = kwargs["due_date"] if kwargs["due_date"] else None

    data = await _put(settings, f"/todos/{todo_id}/subtasks/{subtask_id}", body)
    return f"子任务已更新！\n{_fmt_response(data)}"


async def _complete_subtask(settings: Settings, **kwargs) -> str:
    todo_keyword = kwargs.get("todo_keyword")
    subtask_keyword = kwargs.get("subtask_keyword")
    if not todo_keyword or not subtask_keyword:
        return "请提供父任务关键词和子任务关键词。"

    todo_id, err = await _search_todo_by_keyword(settings, todo_keyword)
    if err:
        return err

    subtask_id, err = await _search_subtask_by_keyword(settings, todo_id, subtask_keyword)
    if err:
        return err

    data = await _patch(settings, f"/todos/{todo_id}/subtasks/{subtask_id}/complete")
    return f"子任务状态已切换！\n{_fmt_response(data)}"


async def _delete_subtask(settings: Settings, **kwargs) -> str:
    todo_keyword = kwargs.get("todo_keyword")
    subtask_keyword = kwargs.get("subtask_keyword")
    if not todo_keyword or not subtask_keyword:
        return "请提供父任务关键词和子任务关键词。"

    todo_id, err = await _search_todo_by_keyword(settings, todo_keyword)
    if err:
        return err

    subtask_id, err = await _search_subtask_by_keyword(settings, todo_id, subtask_keyword)
    if err:
        return err

    await _delete(settings, f"/todos/{todo_id}/subtasks/{subtask_id}")
    return "子任务已删除。"


async def _list_subtasks(settings: Settings, **kwargs) -> str:
    todo_keyword = kwargs.get("todo_keyword")
    if not todo_keyword:
        return "请提供父任务关键词。"

    todo_id, err = await _search_todo_by_keyword(settings, todo_keyword)
    if err:
        return err

    data = await _get(settings, f"/todos/{todo_id}/subtasks")
    items = data if isinstance(data, list) else []
    if not items:
        return "该任务暂无子任务。"
    return _fmt_items(items)


# ==================== Comment Handlers ====================
async def _list_comments(settings: Settings, **kwargs) -> str:
    keyword = kwargs.get("keyword")
    if not keyword:
        return "请提供任务关键词。"
    todo_id, err = await _search_todo_by_keyword(settings, keyword)
    if err:
        return err
    data = await _get(settings, f"/todos/{todo_id}/comments")
    return f"评论列表：\n{_fmt_response(data)}"


async def _create_comment(settings: Settings, **kwargs) -> str:
    keyword = kwargs.get("keyword")
    content = kwargs.get("content")
    if not keyword or not content:
        return "请提供任务关键词和评论内容。"
    todo_id, err = await _search_todo_by_keyword(settings, keyword)
    if err:
        return err
    body = {"content": content}
    parent_id = kwargs.get("parent_id")
    if parent_id:
        body["parentId"] = parent_id
    data = await _post(settings, f"/todos/{todo_id}/comments", body)
    return f"评论成功！\n{_fmt_response(data)}"


async def _delete_comment(settings: Settings, **kwargs) -> str:
    todo_id = kwargs.get("todo_id")
    comment_id = kwargs.get("comment_id")
    if not todo_id or not comment_id:
        return "请提供任务ID和评论ID。"
    await _delete(settings, f"/todos/{todo_id}/comments/{comment_id}")
    return "评论已删除。"


# ==================== Statistics Handlers ====================
async def _get_statistics_handler(settings: Settings, **kwargs) -> str:
    params = {}
    if kwargs.get("project_name"):
        pid = await _resolve_name_to_id(settings, "project", kwargs["project_name"])
        if pid:
            params["projectId"] = pid
    if kwargs.get("team_name"):
        tid = await _resolve_name_to_id(settings, "team", kwargs["team_name"])
        if tid:
            params["teamId"] = tid
    data = await _get(settings, "/dashboard/overview", params)
    lines = []
    c = data if isinstance(data, dict) else {}
    lines.append(f"总任务数：{c.get('total', 0)}")
    lines.append(f"已完成：{c.get('completed', 0)}")
    lines.append(f"未完成：{c.get('active', 0)}")
    lines.append(f"即将到期：{c.get('upcoming', 0)}")
    return "\n".join(lines)


async def _generate_report_handler(settings: Settings, **kwargs) -> str:
    params = {"type": "report"}
    if kwargs.get("project_name"):
        pid = await _resolve_name_to_id(settings, "project", kwargs["project_name"])
        if pid:
            params["projectId"] = pid
    if kwargs.get("team_name"):
        tid = await _resolve_name_to_id(settings, "team", kwargs["team_name"])
        if tid:
            params["teamId"] = tid
    for f in ("date_from", "date_to"):
        if kwargs.get(f):
            params[f] = kwargs[f]
    data = await _get(settings, "/dashboard/trends", params)
    return f"报告数据：{data}"


async def _get_calendar_events_handler(settings: Settings, start_date: str, end_date: str, **kwargs) -> str:
    if not start_date or not end_date:
        return "请提供开始日期和结束日期。"

    params = {"date_from": start_date, "date_to": end_date}
    if kwargs.get("project_name"):
        pid = await _resolve_name_to_id(settings, "project", kwargs["project_name"])
        if pid:
            params["projectId"] = pid
    if kwargs.get("team_name"):
        tid = await _resolve_name_to_id(settings, "team", kwargs["team_name"])
        if tid:
            params["teamId"] = tid

    data = await _get(settings, "/calendar/events", params)
    items = data if isinstance(data, list) else []
    if not items:
        return "该时间段内没有待办事项。"
    lines = [f"共 {len(items)} 个事件："]
    for item in items[:30]:
        text = item.get("title") or item.get("text") or ""
        due = item.get("date") or item.get("dueDate") or ""
        lines.append(f"- {due} {text}")
    return "\n".join(lines)


# ==================== Tag Handlers ====================
async def _list_project_tags(settings: Settings, **kwargs) -> str:
    project_name = kwargs.get("project_name")
    if not project_name:
        return "请提供项目名称。"
    pid = await _resolve_name_to_id(settings, "project", project_name)
    if not pid:
        return f"未找到项目「{project_name}」"
    data = await _get(settings, f"/tags/project/{pid}")
    return f"项目「{project_name}」标签：\n{_fmt_response(data)}"


async def _create_project_tag(settings: Settings, **kwargs) -> str:
    project_name = kwargs.get("project_name")
    name = kwargs.get("name")
    if not project_name or not name:
        return "请提供项目名称和标签名称。"
    pid = await _resolve_name_to_id(settings, "project", project_name)
    if not pid:
        return f"未找到项目「{project_name}」"
    body = {"name": name}
    color = kwargs.get("color")
    if color:
        body["color"] = color
    data = await _post(settings, f"/tags/project/{pid}", body)
    return f"项目标签已创建！\n{_fmt_response(data)}"


async def _delete_tag(settings: Settings, **kwargs) -> str:
    tag_id = kwargs.get("tag_id")
    if not tag_id:
        return "请提供要删除的标签ID。"
    await _delete(settings, f"/tags/{tag_id}")
    return "标签已删除。"


async def _list_team_tags(settings: Settings, **kwargs) -> str:
    team_name = kwargs.get("team_name")
    if not team_name:
        return "请提供团队名称。"
    tid = await _resolve_name_to_id(settings, "team", team_name)
    if not tid:
        return f"未找到团队「{team_name}」"
    data = await _get(settings, f"/tags/team/{tid}")
    return f"团队「{team_name}」标签：\n{_fmt_response(data)}"


async def _create_team_tag(settings: Settings, **kwargs) -> str:
    team_name = kwargs.get("team_name")
    name = kwargs.get("name")
    if not team_name or not name:
        return "请提供团队名称和标签名称。"
    tid = await _resolve_name_to_id(settings, "team", team_name)
    if not tid:
        return f"未找到团队「{team_name}」"
    body = {"teamId": tid, "name": name}
    color = kwargs.get("color")
    if color:
        body["color"] = color
    data = await _post(settings, "/tags/team", body)
    return f"团队标签已创建！\n{_fmt_response(data)}"


async def _delete_team_tag(settings: Settings, **kwargs) -> str:
    team_name = kwargs.get("team_name")
    name = kwargs.get("name")
    if not team_name or not name:
        return "请提供团队名称和标签名称。"
    tid = await _resolve_name_to_id(settings, "team", team_name)
    if not tid:
        return f"未找到团队「{team_name}」"
    data = await _get(settings, f"/tags/team/{tid}")
    items = data if isinstance(data, list) else []
    tag_id = None
    for t in items:
        if isinstance(t, dict) and t.get("name") == name:
            tag_id = t.get("id")
            break
    if not tag_id:
        return f"未找到标签「{name}」"
    await _delete(settings, f"/tags/{tag_id}")
    return "标签已删除。"


async def _add_tag_to_todo(settings: Settings, **kwargs) -> str:
    keyword = kwargs.get("keyword")
    tag_id = kwargs.get("tag_id")
    if not keyword or not tag_id:
        return "请提供任务关键词和标签ID。"
    todo_id, err = await _search_todo_by_keyword(settings, keyword)
    if err:
        return err
    await _post(settings, f"/tags/todo/{todo_id}", {"tagId": tag_id})
    return "标签已添加到任务。"


async def _remove_tag_from_todo(settings: Settings, **kwargs) -> str:
    keyword = kwargs.get("keyword")
    tag_id = kwargs.get("tag_id")
    if not keyword or not tag_id:
        return "请提供任务关键词和标签ID。"
    todo_id, err = await _search_todo_by_keyword(settings, keyword)
    if err:
        return err
    await _delete(settings, f"/tags/todo/{todo_id}/{tag_id}")
    return "标签已从任务移除。"


async def _get_todo_tags(settings: Settings, **kwargs) -> str:
    keyword = kwargs.get("keyword")
    if not keyword:
        return "请提供任务关键词。"
    todo_id, err = await _search_todo_by_keyword(settings, keyword)
    if err:
        return err
    data = await _get(settings, f"/tags/todo/{todo_id}")
    return f"任务标签：\n{_fmt_response(data)}"


# ==================== Todo Helpers ====================
async def _get_todo(settings: Settings, **kwargs) -> str:
    keyword = kwargs.get("keyword")
    if not keyword:
        return "请提供要查询的任务关键词。"
    todo_id, err = await _search_todo_by_keyword(settings, keyword)
    if err:
        return err
    data = await _get(settings, f"/todos/{todo_id}")
    return _fmt_response_dict(data)


async def _get_todo_by_id(settings: Settings, **kwargs) -> str:
    todo_id = kwargs.get("todo_id")
    if not todo_id:
        return "请提供任务ID。"
    data = await _get(settings, f"/todos/{todo_id}")
    return _fmt_response_dict(data)


async def _get_project_by_id(settings: Settings, **kwargs) -> str:
    project_id = kwargs.get("project_id")
    if not project_id:
        return "请提供项目ID。"
    data = await _get(settings, f"/projects/{project_id}")
    return _fmt_response_dict(data)


async def _get_team_by_id(settings: Settings, **kwargs) -> str:
    team_id = kwargs.get("team_id")
    if not team_id:
        return "请提供团队ID。"
    data = await _get(settings, f"/teams/{team_id}")
    return _fmt_response_dict(data)


async def _query_todos_by_project(settings: Settings, **kwargs) -> str:
    project_name = kwargs.get("project_name")
    if not project_name:
        return "请提供项目名称。"

    pid = await _resolve_name_to_id(settings, "project", project_name)
    if not pid:
        return f"未找到项目「{project_name}」"

    params = {"projectId": pid}
    if kwargs.get("section_name"):
        sid = await _resolve_section_name(settings, pid, kwargs["section_name"])
        if sid:
            params["sectionId"] = sid
        else:
            return f"项目中没有找到名为「{kwargs['section_name']}」的分区。"
    if kwargs.get("status"):
        params["status"] = kwargs["status"]
    if kwargs.get("category"):
        params["category"] = kwargs["category"]
    if kwargs.get("priority"):
        params["priority"] = kwargs["priority"]
    if kwargs.get("tag_names"):
        params["tagNames"] = kwargs["tag_names"]
    if kwargs.get("date_from"):
        params["dateFrom"] = kwargs["date_from"]
    if kwargs.get("date_to"):
        params["dateTo"] = kwargs["date_to"]

    data = await _get(settings, "/todos/by-project", params)
    return _format_todo_list(data)


async def _move_todo_section(settings: Settings, **kwargs) -> str:
    keyword = kwargs.get("keyword")
    section_name = kwargs.get("section_name")
    if not keyword or not section_name:
        return "请提供要移动的任务关键词和目标分区名称。"

    todo_id, err = await _search_todo_by_keyword(settings, keyword)
    if err:
        return err

    todo_data = await _get(settings, f"/todos/{todo_id}")
    project_id = todo_data.get("projectId") if isinstance(todo_data, dict) else None
    if not project_id:
        return "该任务没有关联项目，无法移动分区。"

    sid = await _resolve_section_name(settings, project_id, section_name)
    if not sid:
        return f"项目中没有找到名为「{section_name}」的分区。"
    data = await _patch(settings, f"/todos/{todo_id}/move-section", {"sectionId": sid})
    return f"任务已移动到「{section_name}」分区！\n{_fmt_response(data)}"


async def _reorder_todos(settings: Settings, **kwargs) -> str:
    ordered = kwargs.get("ordered_keywords")
    if not ordered or not isinstance(ordered, list) or not ordered:
        return "请提供排序后的任务关键词列表。"

    items = []
    for idx, keyword in enumerate(ordered):
        todo_id, err = await _search_todo_by_keyword(settings, keyword)
        if err:
            return f"找不到任务「{keyword}」：{err}"
        items.append({"id": todo_id, "sortOrder": idx})

    await _put(settings, "/todos/reorder", items)
    return "排序已更新。"


async def _import_todos(settings: Settings, **kwargs) -> str:
    project_name = kwargs.get("project_name")
    tasks = kwargs.get("tasks")
    if not project_name or not tasks:
        return "请提供项目名称和任务列表。"
    pid = await _resolve_name_to_id(settings, "project", project_name)
    if not pid:
        return f"未找到项目「{project_name}」"
    items = []
    for t in tasks:
        if not isinstance(t, dict):
            continue
        item = {"text": t.get("title", "")}
        if not item["text"]:
            continue
        if t.get("category"):
            item["category"] = t["category"]
        if t.get("priority"):
            item["priority"] = t["priority"]
        if t.get("due_date"):
            item["dueDate"] = t["due_date"]
        if t.get("completed") is not None:
            item["completed"] = t["completed"]
        items.append(item)

    if not items:
        return "没有有效的任务可以导入。"
    data = await _post(settings, f"/todos/import/{pid}", items)
    return f"成功导入 {len(items)} 条任务！\n{_fmt_response(data)}"


# ==================== Formatters ====================

def _escape(s: str) -> str:
    return s.replace("\n", " ").replace("\r", " ").strip() if s else ""


def _fmt_response(data) -> str:
    if data is None:
        return "操作成功。"
    if isinstance(data, list):
        return _fmt_items(data)
    if isinstance(data, dict):
        if "records" in data:
            records = data.get("records", [])
            total = data.get("total", len(records))
            header = f"共 {total} 条记录：\n"
            return header + _fmt_items(records)
        return _fmt_response_dict(data)
    return str(data)


def _fmt_response_dict(data: dict) -> str:
    parts = []
    if "name" in data:
        parts.append(f"项目名称：{data.get('name')}")
    if "description" in data:
        parts.append(f"描述：{data.get('description')}")
    if "color" in data:
        parts.append(f"颜色：{data.get('color')}")
    if "sections" in data and isinstance(data["sections"], list):
        parts.append(f"分区：{_fmt_sections(data['sections'])}")
    if "teams" in data and isinstance(data["teams"], list):
        team_names = [t.get("name", "") for t in data["teams"] if t.get("name")]
        if team_names:
            parts.append(f"团队：{'、'.join(team_names)}")
    project_name = data.get("projectName")
    if project_name:
        parts.append(f"项目：{project_name}")
    section_name = data.get("sectionName")
    if section_name:
        parts.append(f"分区：{section_name}")
    if "text" in data:
        parts.append(f"标题：{data.get('text')}")
    if "content" in data:
        parts.append(f"内容：{data.get('content')}")
    if parts:
        return " | ".join(parts)
    return json.dumps(data, ensure_ascii=False, indent=2)


def _fmt_sections(sections: list) -> str:
    if not sections:
        return "（无分区）"
    return "、".join(
        s.get("name", f"分区{i+1}")
        for i, s in enumerate(sections)
    )


def _fmt_items(data: list) -> str:
    if not data:
        return "（无）"
    lines = []
    for idx, item in enumerate(data, 1):
        if not isinstance(item, dict):
            lines.append(f"{idx}. {item}")
            continue
        title = item.get("title") or item.get("text") or item.get("name") or f"项目 {idx}"
        parts = [str(idx) + ".", title]
        meta_parts = []
        status = item.get("status")
        if status:
            status_map = {"pending": "待办", "in_progress": "进行中", "completed": "已完成"}
            meta_parts.append(status_map.get(status, status))
        priority = item.get("priority")
        if priority:
            meta_parts.append(priority)
        category = item.get("category")
        if category:
            meta_parts.append(category)
        due = item.get("dueDate") or item.get("due_date")
        if due:
            meta_parts.append(f"截止: {due}")
        project_name = item.get("projectName")
        if project_name:
            meta_parts.append(f"项目: {project_name}")
        section_name = item.get("sectionName")
        if section_name:
            meta_parts.append(f"分区: {section_name}")
        if meta_parts:
            parts.append("(" + " | ".join(meta_parts) + ")")
        lines.append(" ".join(parts))
    return "\n".join(lines)



# ==================== Handler Map ====================
_TOOL_HANDLERS: dict[str, callable] = {
    # Todo
    "create_todo": _create_todo,
    "update_todo": _update_todo,
    "complete_todo": _complete_todo,
    "delete_todo": _delete_todo,
    "search_todos": _search_todos,
    "get_todo": _get_todo,
    "restore_todo": _restore_todo,
    "delete_todo_permanent": _delete_todo_permanent,
    "list_deleted_todos": _list_deleted_todos,
    "get_todo_by_id": _get_todo_by_id,
    "reorder_todos": _reorder_todos,
    "import_todos": _import_todos,
    "move_todo_section": _move_todo_section,
    "query_todos_by_project": _query_todos_by_project,
    # Project
    "create_project": _create_project,
    "update_project": _update_project,
    "delete_project": _delete_project,
    "list_projects": _list_projects,
    "list_sections": _list_project_sections,
    "list_project_sections": _list_project_sections,
    "create_section": _create_section,
    "update_section": _update_section,
    "delete_section": _delete_section,
    "reorder_sections": _reorder_sections,
    "get_project_by_id": _get_project_by_id,
    # Team
    "create_team": _create_team,
    "update_team": _update_team,
    "delete_team": _delete_team,
    "list_teams": _list_teams,
    "list_team_members": _list_team_members,
    "add_team_member": _add_team_member,
    "remove_team_member": _remove_team_member,
    "join_team": _join_team,
    "update_member_role": _update_member_role,
    "get_team_by_id": _get_team_by_id,
    # Subtask
    "create_subtask": _create_subtask,
    "update_subtask": _update_subtask,
    "complete_subtask": _complete_subtask,
    "delete_subtask": _delete_subtask,
    "list_subtasks": _list_subtasks,
    # Statistics
    "get_statistics": _get_statistics_handler,
    "generate_report": _generate_report_handler,
    "get_calendar_events": _get_calendar_events_handler,
    # Tag
    "list_project_tags": _list_project_tags,
    "create_project_tag": _create_project_tag,
    "list_team_tags": _list_team_tags,
    "create_team_tag": _create_team_tag,
    "delete_tag": _delete_tag,
    "delete_team_tag": _delete_team_tag,
    "add_tag_to_todo": _add_tag_to_todo,
    "remove_tag_from_todo": _remove_tag_from_todo,
    "get_todo_tags": _get_todo_tags,
    # Comment
    "list_comments": _list_comments,
    "create_comment": _create_comment,
    "delete_comment": _delete_comment,
}
