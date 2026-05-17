"""名称解析工具 —— 将用户/LLM 输入的名称转换为后端 ID。

为什么需要名称解析：
LLM 理解自然语言名称（"我的项目"、"张三"），但 Java 后端 API 需要数字 ID。
这些函数负责完成名称 → ID 的映射，是 LLM 友好输入与后端 API 之间的桥梁。
"""

import logging

from app.tools.http_client import _get

logger = logging.getLogger(__name__)


async def _resolve_name_to_id(name_type: str, name: str) -> int | None:
    """通用名称到 ID 解析。支持 project 和 team 两种类型。"""
    if not name:
        return None
    try:
        if name_type == "project":
            data = await _get("/projects", {"name": name})
            for p in (data if isinstance(data, list) else []):
                if isinstance(p, dict) and p.get("name") == name:
                    return p.get("id")
        elif name_type == "team":
            data = await _get("/teams", {"name": name})
            for t in (data if isinstance(data, list) else []):
                if isinstance(t, dict) and t.get("name") == name:
                    return t.get("id")
    except Exception as e:
        logger.warning("Failed to resolve %s name '%s': %s", name_type, name, e)
    return None


async def _resolve_assignee_name(
    assignee_name: str, *,
    team_id: int | None = None, project_id: int | None = None,
) -> list[dict]:
    """负责人名称解析 —— 通过团队成员列表查找 userId。

    为什么跨团队搜索：用户可能在多个团队中，如果只给了一个 project_id，
    需要查找该项目关联的所有团队，在其中逐一匹配用户名。

    返回 [{"userId", "teamId", "teamName"}, ...]，可能多个结果。
    空列表表示未找到。
    """
    if not assignee_name:
        return []

    team_ids: list[int] = []
    if team_id:
        team_ids = [team_id]
    elif project_id:
        try:
            teams_resp = await _get(f"/projects/{project_id}/teams")
            if isinstance(teams_resp, list):
                team_ids = [t["id"] for t in teams_resp if isinstance(t, dict) and t.get("id")]
        except Exception as e:
            logger.warning("Failed to get teams for project %s: %s", project_id, e)

    if not team_ids:
        return []

    results: list[dict] = []
    for tid in team_ids:
        try:
            data = await _get(f"/teams/{tid}")
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


async def _resolve_section_name(project_id: int | None, section_name: str) -> int | None:
    """通过分区名查找分区 ID。"""
    if not section_name or not project_id:
        return None
    try:
        data = await _get(f"/projects/{project_id}/sections")
        for s in (data if isinstance(data, list) else []):
            if isinstance(s, dict) and s.get("name") == section_name:
                return s.get("id")
    except Exception as e:
        logger.warning("Failed to resolve section name '%s' for project %s: %s", section_name, project_id, e)
    return None


async def _search_todo_by_keyword(keyword: str) -> tuple[int | None, str]:
    """通过关键词搜索单个任务。返回 (todo_id, error_msg)。

    为什么需要这个函数：LLM 常以自然语言关键词引用任务（"那个关于登录的bug"），
    而 API 需要数字 ID。此函数处理唯一匹配、无匹配、模糊匹配三种情况。
    """
    try:
        result = await _get("/todos/search", {"q": keyword})
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


async def _search_subtask_by_keyword(todo_id: int, keyword: str) -> tuple[int | None, str]:
    """在指定任务内通过关键词搜索单个子任务。返回 (subtask_id, error_msg)。"""
    try:
        result = await _get(f"/todos/{todo_id}/subtasks")
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


async def _resolve_name_and_section(kwargs: dict, body: dict) -> str | None:
    """批量解析名称字段（project_name, team_name, section_name）并填充到 body。

    为什么集中处理：handler 中几乎每个操作都需要解析名称 → ID，
    统一处理减少重复代码。返回错误字符串表示致命失败，None 表示成功。
    """
    if "project_name" in kwargs and kwargs["project_name"]:
        pid = await _resolve_name_to_id("project", kwargs["project_name"])
        if pid:
            body["projectId"] = pid

    if "team_name" in kwargs and kwargs["team_name"]:
        tid = await _resolve_name_to_id("team", kwargs["team_name"])
        if tid:
            body["teamId"] = tid

    if "section_name" in kwargs and kwargs["section_name"]:
        project_id = body.get("projectId")
        if project_id:
            sid = await _resolve_section_name(project_id, kwargs["section_name"])
            if sid:
                body["sectionId"] = sid

    return None
