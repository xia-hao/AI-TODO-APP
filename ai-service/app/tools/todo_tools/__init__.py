"""todo_tools 包入口 —— 工具注册和 Handler 映射。

关键调用点：register_all() 由 load_tool_packages() 在启动时调用，
负责将所有工具定义和 handler 注册到 ToolRegistry。

PROMPT_SUFFIX 会被自动拼接到系统提示词末尾，
加新工具包时只需在包内定义此变量，无需修改 config.py。
"""

PROMPT_SUFFIX = (
    "你可以使用提供的工具来：管理任务（创建、更新、删除、查询、导入、搜索）、"
    "管理项目（创建、更新、删除、分区管理、排序）、"
    "管理团队（创建、解散、成员管理、角色更新）、"
    "管理子任务和评论、查看统计数据和生成报告、管理标签。"
)

import logging

from app.tools.tool_settings import set_tool_settings
from app.tools.todo_tools.definitions import TOOL_DEFS
from app.tools.http_client import _base
from app.tools.todo_tools.handlers import (
    _create_todo,
    _update_todo,
    _complete_todo,
    _delete_todo,
    _search_todos,
    _get_todo,
    _restore_todo,
    _delete_todo_permanent,
    _list_deleted_todos,
    _get_todo_by_id,
    _reorder_todos,
    _import_todos,
    _move_todo_section,
    _query_todos_by_project,
    _create_project,
    _update_project,
    _delete_project,
    _list_projects,
    _list_project_sections,
    _create_section,
    _update_section,
    _delete_section,
    _reorder_sections,
    _get_project_by_id,
    _create_team,
    _update_team,
    _delete_team,
    _list_teams,
    _list_team_members,
    _add_team_member,
    _remove_team_member,
    _join_team,
    _update_member_role,
    _get_team_by_id,
    _create_subtask,
    _update_subtask,
    _complete_subtask,
    _delete_subtask,
    _list_subtasks,
    _get_statistics_handler,
    _generate_report_handler,
    _get_calendar_events_handler,
    _list_project_tags,
    _create_project_tag,
    _list_team_tags,
    _create_team_tag,
    _delete_tag,
    _delete_team_tag,
    _add_tag_to_todo,
    _remove_tag_from_todo,
    _get_todo_tags,
    _list_comments,
    _create_comment,
    _delete_comment,
)

logger = logging.getLogger(__name__)


# ============================================================
# 工具名称 → Handler 函数映射
# 注意：list_sections 和 list_project_sections 共用同一 handler
# ============================================================

_TOOL_HANDLERS: dict[str, object] = {
    # 任务 (14)
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
    # 项目 (10)
    "create_project": _create_project,
    "update_project": _update_project,
    "delete_project": _delete_project,
    "list_projects": _list_projects,
    "list_sections": _list_project_sections,
    "create_section": _create_section,
    "update_section": _update_section,
    "delete_section": _delete_section,
    "reorder_sections": _reorder_sections,
    "get_project_by_id": _get_project_by_id,
    # 团队 (10)
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
    # 子任务 (5)
    "create_subtask": _create_subtask,
    "update_subtask": _update_subtask,
    "complete_subtask": _complete_subtask,
    "delete_subtask": _delete_subtask,
    "list_subtasks": _list_subtasks,
    # 统计 (3)
    "get_statistics": _get_statistics_handler,
    "generate_report": _generate_report_handler,
    "get_calendar_events": _get_calendar_events_handler,
    # 标签 (9)
    "list_project_tags": _list_project_tags,
    "create_project_tag": _create_project_tag,
    "list_team_tags": _list_team_tags,
    "create_team_tag": _create_team_tag,
    "delete_tag": _delete_tag,
    "delete_team_tag": _delete_team_tag,
    "add_tag_to_todo": _add_tag_to_todo,
    "remove_tag_from_todo": _remove_tag_from_todo,
    "get_todo_tags": _get_todo_tags,
    # 评论 (3)
    "list_comments": _list_comments,
    "create_comment": _create_comment,
    "delete_comment": _delete_comment,
}


def register_all(registry, settings) -> None:
    """注册所有工具到 ToolRegistry。

    关键调用点：启动时由 load_tool_packages() 调用。
    1. 先将 settings 注入全局单例，供 handler 按需读取
    2. 遍历 TOOL_DEFS，将每个工具定义与 handler 绑定注册

    _safe_wrapper 确保单个工具异常不会中断整个 SSE 流。
    """
    # 注入 settings 到单例 —— handler 内部通过 get_tool_settings() 读取
    set_tool_settings(settings)

    async def _safe_wrapper(handler, **kwargs) -> str:
        """异常隔离包装器 —— 单个工具失败不影响 Tool Loop 其他迭代。"""
        try:
            return await handler(**kwargs)
        except Exception as e:
            logger.error("Handler %s failed: %s", handler.__name__, e, exc_info=True)
            return f"操作失败：{e}"

    for tool_def in TOOL_DEFS:
        name = tool_def["function"]["name"]
        handler = _TOOL_HANDLERS[name]

        # 闭包延迟绑定：用默认参数捕获当前 handler 引用
        def _make_wrapped(h=handler):
            async def _wrapped(**kwargs):
                return await _safe_wrapper(h, **kwargs)
            return _wrapped

        wrapped = _make_wrapped()
        wrapped.__name__ = name
        registry.register(tool_def, wrapped)

    logger.info("Registered %d todo tools — backend=%s", len(TOOL_DEFS), _base())
