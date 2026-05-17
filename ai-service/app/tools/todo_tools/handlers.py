"""工具 Handler 实现 —— 对 Java 后端的实际 API 调用逻辑。

按业务域组织，每个 handler 签名统一为 async def handler(**kwargs) -> str。
所有 handler 通过 get_tool_settings() 读取配置，不再接收 settings 参数。

Tool Loop 中的调用链：
  chat.event_generator → tool_registry.execute(name, args)
    → _safe_wrapper → handler(**args) → HTTP 请求 → 格式化 → 返回文本
"""

import asyncio
import logging

from app.tools.tool_settings import get_tool_settings
from app.tools.http_client import _get, _post, _put, _patch, _delete
from app.tools.todo_tools.name_resolvers import (
    _resolve_name_to_id,
    _resolve_assignee_name,
    _resolve_section_name,
    _search_todo_by_keyword,
    _search_subtask_by_keyword,
    _resolve_name_and_section,
)
from app.tools.todo_tools.formatters import (
    _escape,
    _fmt_response,
    _fmt_response_dict,
    _fmt_items,
    _format_todo_list,
)

logger = logging.getLogger(__name__)


# ==================== 任务 (14) ====================

async def _create_todo(**kwargs) -> str:
    """创建任务 —— 关键调用点：名称→ID 解析 + 负责人跨团队搜索。"""
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

    # 字段名映射：LLM 友好名称 → Java 后端字段名
    field_map = {"title": "text", "due_date": "dueDate", "team_id": "teamId"}
    for old_k, new_k in field_map.items():
        if old_k in body:
            body[new_k] = body.pop(old_k)

    await _resolve_name_and_section(kwargs, body)

    if kwargs.get("assignee_name"):
        matches = await _resolve_assignee_name(
            kwargs["assignee_name"],
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

    data = await _post("/todos", body)
    return f"任务创建成功！\n{_fmt_response(data)}"


async def _update_todo(**kwargs) -> str:
    """更新任务 —— 先获取现有数据以确保必填字段不丢失，再合并用户提供的字段。"""
    keyword = kwargs.pop("keyword", None)
    if not keyword:
        return "请提供要更新的任务关键词。"

    todo_id, err = await _search_todo_by_keyword(keyword)
    if err:
        return err

    # 先获取现有任务数据作为基准，防止必填字段丢失
    existing = None
    try:
        existing = await _get(f"/todos/{todo_id}")
    except Exception as e:
        return f"获取任务详情失败：{e}"
    if not isinstance(existing, dict):
        return "未找到匹配的任务。"

    # 合并：现有值作为默认，用户提供的覆盖
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
        pid = await _resolve_name_to_id("project", kwargs["project_name"])
        if pid:
            body["projectId"] = pid
    if kwargs.get("section_name"):
        sid = await _resolve_section_name(body.get("projectId"), kwargs["section_name"])
        if sid:
            body["sectionId"] = sid

    if kwargs.get("team_name"):
        tid = await _resolve_name_to_id("team", kwargs["team_name"])
        if tid:
            body["teamId"] = tid

    if "team_id" in kwargs:
        body["teamId"] = kwargs["team_id"]

    if "assignee_name" in kwargs:
        assignee_name = kwargs["assignee_name"]
        if assignee_name:
            matches = await _resolve_assignee_name(
                assignee_name,
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
        data = await _put(f"/todos/{todo_id}", body)
    except Exception as e:
        return f"更新任务失败：{e}"

    return f"任务已更新！\n{_fmt_response(data)}"


async def _complete_todo(**kwargs) -> str:
    keyword = kwargs.get("keyword")
    if not keyword:
        return "请提供要操作的任务关键词。"
    todo_id, err = await _search_todo_by_keyword(keyword)
    if err:
        return err
    data = await _patch(f"/todos/{todo_id}/complete")
    return f"任务状态已切换！\n{_fmt_response(data)}"


async def _delete_todo(**kwargs) -> str:
    keyword = kwargs.get("keyword")
    if not keyword:
        return "请提供要删除的任务关键词。"
    todo_id, err = await _search_todo_by_keyword(keyword)
    if err:
        return err
    data = await _delete(f"/todos/{todo_id}")
    return f"任务已移入回收站！\n{_fmt_response(data)}"


async def _restore_todo(**kwargs) -> str:
    keyword = kwargs.get("keyword")
    if not keyword:
        return "请提供要恢复的任务关键词。"

    data = await _get("/todos/deleted")
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
    await _patch(f"/todos/{todo_id}/restore")
    return "任务已恢复！"


async def _delete_todo_permanent(**kwargs) -> str:
    """永久删除 —— 先在活跃任务中查找，未找到再搜索回收站。"""
    keyword = kwargs.get("keyword")
    if not keyword:
        return "请提供要永久删除的任务关键词。"

    # 先搜索活跃任务
    todo_id, err = await _search_todo_by_keyword(keyword)
    if not err:
        await _delete(f"/todos/{todo_id}/permanent")
        return "任务已永久删除！"

    # 未找到活跃任务，搜索回收站
    data = await _get("/todos/deleted")
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
    await _delete(f"/todos/{todo_id}/permanent")
    return "任务已永久删除！"


async def _list_deleted_todos(**kwargs) -> str:
    data = await _get("/todos/deleted")
    items = data if isinstance(data, list) else []
    if not items:
        return "回收站为空。"
    return _fmt_items(items)


async def _search_todos(**kwargs) -> str:
    params = {}
    for k, v in kwargs.items():
        if v is None:
            continue
        if k == "team_name":
            tid = await _resolve_name_to_id("team", v)
            if tid:
                params["teamId"] = tid
        elif k == "keyword":
            params["q"] = v
        else:
            params[k] = v

    if "project_name" in params:
        pid = await _resolve_name_to_id("project", params.pop("project_name"))
        if pid:
            params["projectId"] = pid

    data = await _get("/todos", params)
    return _format_todo_list(data)


# ==================== 项目 (8) ====================

async def _create_project(**kwargs) -> str:
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
                    tid = await _resolve_name_to_id("team", name)
                    if tid:
                        team_ids.append(tid)
                if team_ids:
                    body["teamIds"] = team_ids
            continue
        body[k] = v
    data = await _post("/projects", body)
    return f"项目创建成功！\n{_fmt_response(data)}"


async def _update_project(**kwargs) -> str:
    name = kwargs.get("name")
    if not name:
        return "请提供项目名称。"
    pid = await _resolve_name_to_id("project", name)
    if not pid:
        return f"未找到名为「{name}」的项目。"

    body = {}
    if "new_name" in kwargs and kwargs["new_name"]:
        body["name"] = kwargs["new_name"]
    else:
        try:
            existing = await _get(f"/projects/{pid}")
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
            for nm in kwargs["team_names"]:
                tid = await _resolve_name_to_id("team", nm)
                if tid:
                    team_ids.append(tid)
            if team_ids:
                body["teamIds"] = team_ids
    data = await _put(f"/projects/{pid}", body)
    return f"项目已更新！\n{_fmt_response(data)}"


async def _delete_project(**kwargs) -> str:
    name = kwargs.get("name")
    if not name:
        return "请提供要删除的项目名称。"
    pid = await _resolve_name_to_id("project", name)
    if not pid:
        return f"未找到名为「{name}」的项目。"
    await _delete(f"/projects/{pid}")
    return f"项目「{name}」已删除。"


async def _list_projects(**kwargs) -> str:
    """列出项目及其分区 —— 并发获取分区以避免 N+1 串行请求。

    为什么用 asyncio.gather：当项目数 >5 时，串行请求的延迟叠加显著变慢。
    并发请求将总耗时从 N * latency 降到 max(latency)。
    """
    data = await _get("/projects")
    if not isinstance(data, list):
        return f"项目列表：\n{_fmt_response(data)}"

    # 并发获取所有项目的分区列表
    async def _fetch_sections(proj):
        pid = proj.get("id")
        if not pid:
            return []
        try:
            sec_data = await _get(f"/projects/{pid}/sections")
            return [s.get("name", "") for s in (sec_data if isinstance(sec_data, list) else [])]
        except Exception:
            return []

    section_results = await asyncio.gather(*[_fetch_sections(p) for p in data])

    lines = []
    for idx, (proj, sections) in enumerate(zip(data, section_results), 1):
        name = proj.get("name", f"项目{idx}")
        parts = [str(idx) + ".", name]
        if sections:
            parts.append(f"（分区：{'、'.join(sections)}）")
        lines.append(" ".join(parts))
    return f"项目列表：\n" + "\n".join(lines) if lines else "（无）"


async def _list_project_sections(**kwargs) -> str:
    """获取项目分区列表 —— list_sections 和 list_project_sections 共用此 handler。"""
    project_name = kwargs.get("project_name")
    if not project_name:
        return "请提供项目名称。"
    pid = await _resolve_name_to_id("project", project_name)
    if not pid:
        return f"未找到项目「{project_name}」"
    data = await _get(f"/projects/{pid}/sections")
    items = data if isinstance(data, list) else []
    if not items:
        return f"项目「{project_name}」下暂无分区。"
    lines = [f"项目「{project_name}」的分区（共 {len(items)} 个）："]
    for s in items:
        name = s.get("name") or ""
        lines.append(f"- {name}")
    return "\n".join(lines)


async def _create_section(**kwargs) -> str:
    project_name = kwargs.get("project_name")
    name = kwargs.get("name")
    if not project_name or not name:
        return "请提供项目名称和分区名称。"
    pid = await _resolve_name_to_id("project", project_name)
    if not pid:
        return f"未找到项目「{project_name}」"
    body = {"name": name, "projectId": pid}
    data = await _post(f"/projects/{pid}/sections", body)
    return f"分区创建成功！\n{_fmt_response(data)}"


async def _update_section(**kwargs) -> str:
    project_name = kwargs.get("project_name")
    name = kwargs.get("name")
    new_name = kwargs.get("new_name")
    if not project_name or not name or not new_name:
        return "请提供项目名称、当前分区名称和新分区名称。"
    pid = await _resolve_name_to_id("project", project_name)
    if not pid:
        return f"未找到项目「{project_name}」"
    sid = await _resolve_section_name(pid, name)
    if not sid:
        return f"项目中没有找到名为「{name}」的分区。"
    body = {"name": new_name}
    data = await _put(f"/projects/{pid}/sections/{sid}", body)
    return f"分区已更新！\n{_fmt_response(data)}"


async def _delete_section(**kwargs) -> str:
    project_name = kwargs.get("project_name")
    name = kwargs.get("name")
    if not project_name or not name:
        return "请提供项目名称和分区名称。"
    pid = await _resolve_name_to_id("project", project_name)
    if not pid:
        return f"未找到项目「{project_name}」"
    sid = await _resolve_section_name(pid, name)
    if not sid:
        return f"项目中没有找到名为「{name}」的分区。"
    await _delete(f"/projects/{pid}/sections/{sid}")
    return "分区已删除。"


async def _reorder_sections(**kwargs) -> str:
    project_name = kwargs.get("project_name")
    ordered_ids = kwargs.get("ordered_ids")
    if not project_name or not ordered_ids:
        return "请提供项目名称和排序后的分区ID列表。"
    pid = await _resolve_name_to_id("project", project_name)
    if not pid:
        return f"未找到项目「{project_name}」"
    await _put(f"/projects/{pid}/sections/reorder", ordered_ids)
    return "分区排序已更新。"


# ==================== 团队 (9) ====================

async def _create_team(**kwargs) -> str:
    if not kwargs.get("name"):
        return "请提供团队名称。"
    body = {k: v for k, v in kwargs.items() if v is not None}
    data = await _post("/teams", body)
    return f"团队创建成功！\n{_fmt_response(data)}"


async def _update_team(**kwargs) -> str:
    name = kwargs.get("name")
    if not name:
        return "请提供团队名称。"
    tid = await _resolve_name_to_id("team", name)
    if not tid:
        return f"未找到名为「{name}」的团队。"
    body = {}
    if "new_name" in kwargs and kwargs["new_name"]:
        body["name"] = kwargs["new_name"]
    else:
        try:
            existing = await _get(f"/teams/{tid}")
            body["name"] = existing.get("name", name)
        except Exception:
            body["name"] = name
    if "description" in kwargs and kwargs["description"]:
        body["description"] = kwargs["description"]
    data = await _put(f"/teams/{tid}", body)
    return f"团队已更新！\n{_fmt_response(data)}"


async def _delete_team(**kwargs) -> str:
    name = kwargs.get("name")
    if not name:
        return "请提供要解散的团队名称。"
    tid = await _resolve_name_to_id("team", name)
    if not tid:
        return f"未找到名为「{name}」的团队。"
    await _delete(f"/teams/{tid}")
    return f"团队「{name}」已解散。"


async def _list_teams(**kwargs) -> str:
    data = await _get("/teams")
    return f"团队列表：\n{_fmt_response(data)}"


async def _list_team_members(**kwargs) -> str:
    team_name = kwargs.get("team_name")
    if not team_name:
        return "请提供团队名称。"
    tid = await _resolve_name_to_id("team", team_name)
    if not tid:
        return f"未找到团队「{team_name}」。"
    data = await _get(f"/teams/{tid}")
    members = data.get("members", []) if isinstance(data, dict) else []
    if not members:
        return f"团队「{team_name}」暂无成员。"
    lines = [f"团队「{team_name}」成员列表："]
    for idx, m in enumerate(members, 1):
        name = m.get("displayName") or m.get("username", "")
        role = m.get("role", "")
        lines.append(f"  {idx}. {name}（{role}）")
    return "\n".join(lines)


async def _add_team_member(**kwargs) -> str:
    team_name = kwargs.get("team_name")
    username = kwargs.get("username")
    role = kwargs.get("role")
    if not team_name or not username or not role:
        return "请提供团队名称、成员用户名和角色。"
    tid = await _resolve_name_to_id("team", team_name)
    if not tid:
        return f"未找到团队「{team_name}」"
    body = {"username": username, "role": role}
    data = await _post(f"/teams/{tid}/members", body)
    return f"成员添加成功！\n{_fmt_response(data)}"


async def _remove_team_member(**kwargs) -> str:
    team_name = kwargs.get("team_name")
    username = kwargs.get("username")
    if not team_name or not username:
        return "请提供团队名称和成员用户名。"
    tid = await _resolve_name_to_id("team", team_name)
    if not tid:
        return f"未找到团队「{team_name}」"
    data = await _get(f"/teams/{tid}")
    members = data.get("members", []) if isinstance(data, dict) else []
    uid = None
    for m in members:
        if isinstance(m, dict) and m.get("username") == username:
            uid = m.get("userId")
            break
    if not uid:
        return f"未在团队中找到成员「{username}」。"
    await _delete(f"/teams/{tid}/members/{uid}")
    return f"成员「{username}」已从团队移除。"


async def _join_team(**kwargs) -> str:
    invite_code = kwargs.get("invite_code")
    if not invite_code:
        return "请提供邀请码。"
    data = await _post("/teams/join", {"inviteCode": invite_code})
    return f"加入团队成功！\n{_fmt_response(data)}"


async def _update_member_role(**kwargs) -> str:
    team_name = kwargs.get("team_name")
    username = kwargs.get("username")
    role = kwargs.get("role")
    if not team_name or not username or not role:
        return "请提供团队名称、成员用户名和角色。"
    tid = await _resolve_name_to_id("team", team_name)
    if not tid:
        return f"未找到团队「{team_name}」"
    data = await _get(f"/teams/{tid}")
    members = data.get("members", []) if isinstance(data, dict) else []
    user_id = None
    for m in members:
        if m.get("username") == username or m.get("displayName") == username:
            user_id = m.get("userId")
            break
    if not user_id:
        return f"未在团队「{team_name}」中找到成员「{username}」"
    await _put(f"/teams/{tid}/members/{user_id}/role", {"role": role})
    return f"成员「{username}」的角色已更新为 {role}。"


# ==================== 子任务 (5) ====================

async def _create_subtask(**kwargs) -> str:
    todo_keyword = kwargs.get("todo_keyword")
    title = kwargs.get("title")
    if not todo_keyword or not title:
        return "请提供父任务关键词和子任务标题。"
    todo_id, err = await _search_todo_by_keyword(todo_keyword)
    if err:
        return err
    body = {}
    if title:
        body["text"] = title
    due_date = kwargs.get("due_date")
    if due_date:
        body["dueDate"] = due_date
    data = await _post(f"/todos/{todo_id}/subtasks", body)
    return f"子任务创建成功！\n{_fmt_response(data)}"


async def _update_subtask(**kwargs) -> str:
    todo_keyword = kwargs.get("todo_keyword")
    subtask_keyword = kwargs.get("subtask_keyword")
    if not todo_keyword or not subtask_keyword:
        return "请提供父任务关键词和子任务关键词。"
    todo_id, err = await _search_todo_by_keyword(todo_keyword)
    if err:
        return err
    subtask_id, err = await _search_subtask_by_keyword(todo_id, subtask_keyword)
    if err:
        return err
    body = {}
    title_provided = "title" in kwargs and kwargs["title"]
    if title_provided:
        body["text"] = kwargs["title"]
    else:
        try:
            result = await _get(f"/todos/{todo_id}/subtasks")
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
    data = await _put(f"/todos/{todo_id}/subtasks/{subtask_id}", body)
    return f"子任务已更新！\n{_fmt_response(data)}"


async def _complete_subtask(**kwargs) -> str:
    todo_keyword = kwargs.get("todo_keyword")
    subtask_keyword = kwargs.get("subtask_keyword")
    if not todo_keyword or not subtask_keyword:
        return "请提供父任务关键词和子任务关键词。"
    todo_id, err = await _search_todo_by_keyword(todo_keyword)
    if err:
        return err
    subtask_id, err = await _search_subtask_by_keyword(todo_id, subtask_keyword)
    if err:
        return err
    data = await _patch(f"/todos/{todo_id}/subtasks/{subtask_id}/complete")
    return f"子任务状态已切换！\n{_fmt_response(data)}"


async def _delete_subtask(**kwargs) -> str:
    todo_keyword = kwargs.get("todo_keyword")
    subtask_keyword = kwargs.get("subtask_keyword")
    if not todo_keyword or not subtask_keyword:
        return "请提供父任务关键词和子任务关键词。"
    todo_id, err = await _search_todo_by_keyword(todo_keyword)
    if err:
        return err
    subtask_id, err = await _search_subtask_by_keyword(todo_id, subtask_keyword)
    if err:
        return err
    await _delete(f"/todos/{todo_id}/subtasks/{subtask_id}")
    return "子任务已删除。"


async def _list_subtasks(**kwargs) -> str:
    todo_keyword = kwargs.get("todo_keyword")
    if not todo_keyword:
        return "请提供父任务关键词。"
    todo_id, err = await _search_todo_by_keyword(todo_keyword)
    if err:
        return err
    data = await _get(f"/todos/{todo_id}/subtasks")
    items = data if isinstance(data, list) else []
    if not items:
        return "该任务暂无子任务。"
    return _fmt_items(items)


# ==================== 评论 (3) ====================

async def _list_comments(**kwargs) -> str:
    keyword = kwargs.get("keyword")
    if not keyword:
        return "请提供任务关键词。"
    todo_id, err = await _search_todo_by_keyword(keyword)
    if err:
        return err
    data = await _get(f"/todos/{todo_id}/comments")
    return f"评论列表：\n{_fmt_response(data)}"


async def _create_comment(**kwargs) -> str:
    keyword = kwargs.get("keyword")
    content = kwargs.get("content")
    if not keyword or not content:
        return "请提供任务关键词和评论内容。"
    todo_id, err = await _search_todo_by_keyword(keyword)
    if err:
        return err
    body = {"content": content}
    parent_id = kwargs.get("parent_id")
    if parent_id:
        body["parentId"] = parent_id
    data = await _post(f"/todos/{todo_id}/comments", body)
    return f"评论成功！\n{_fmt_response(data)}"


async def _delete_comment(**kwargs) -> str:
    todo_id = kwargs.get("todo_id")
    comment_id = kwargs.get("comment_id")
    if not todo_id or not comment_id:
        return "请提供任务ID和评论ID。"
    await _delete(f"/todos/{todo_id}/comments/{comment_id}")
    return "评论已删除。"


# ==================== 统计 (3) ====================

async def _get_statistics_handler(**kwargs) -> str:
    params = {}
    if kwargs.get("project_name"):
        pid = await _resolve_name_to_id("project", kwargs["project_name"])
        if pid:
            params["projectId"] = pid
    if kwargs.get("team_name"):
        tid = await _resolve_name_to_id("team", kwargs["team_name"])
        if tid:
            params["teamId"] = tid
    data = await _get("/dashboard/overview", params)
    lines = []
    c = data if isinstance(data, dict) else {}
    lines.append(f"总任务数：{c.get('total', 0)}")
    lines.append(f"已完成：{c.get('completed', 0)}")
    lines.append(f"未完成：{c.get('active', 0)}")
    lines.append(f"即将到期：{c.get('upcoming', 0)}")
    return "\n".join(lines)


async def _generate_report_handler(**kwargs) -> str:
    params = {"type": "report"}
    if kwargs.get("project_name"):
        pid = await _resolve_name_to_id("project", kwargs["project_name"])
        if pid:
            params["projectId"] = pid
    if kwargs.get("team_name"):
        tid = await _resolve_name_to_id("team", kwargs["team_name"])
        if tid:
            params["teamId"] = tid
    for f in ("date_from", "date_to"):
        if kwargs.get(f):
            params[f] = kwargs[f]
    data = await _get("/dashboard/trends", params)
    return f"报告数据：{data}"


async def _get_calendar_events_handler(**kwargs) -> str:
    """获取日历事件 —— date_from/date_to 来自 JSON Schema required 字段。"""
    start_date = kwargs.get("date_from", "")
    end_date = kwargs.get("date_to", "")
    if not start_date or not end_date:
        return "请提供开始日期和结束日期。"

    params = {"date_from": start_date, "date_to": end_date}
    if kwargs.get("project_name"):
        pid = await _resolve_name_to_id("project", kwargs["project_name"])
        if pid:
            params["projectId"] = pid
    if kwargs.get("team_name"):
        tid = await _resolve_name_to_id("team", kwargs["team_name"])
        if tid:
            params["teamId"] = tid

    data = await _get("/calendar/events", params)
    items = data if isinstance(data, list) else []
    if not items:
        return "该时间段内没有待办事项。"
    lines = [f"共 {len(items)} 个事件："]
    for item in items[:30]:
        text = item.get("title") or item.get("text") or ""
        due = item.get("date") or item.get("dueDate") or ""
        lines.append(f"- {due} {text}")
    return "\n".join(lines)


# ==================== 标签 (9) ====================

async def _list_project_tags(**kwargs) -> str:
    project_name = kwargs.get("project_name")
    if not project_name:
        return "请提供项目名称。"
    pid = await _resolve_name_to_id("project", project_name)
    if not pid:
        return f"未找到项目「{project_name}」"
    data = await _get(f"/tags/project/{pid}")
    return f"项目「{project_name}」标签：\n{_fmt_response(data)}"


async def _create_project_tag(**kwargs) -> str:
    project_name = kwargs.get("project_name")
    name = kwargs.get("name")
    if not project_name or not name:
        return "请提供项目名称和标签名称。"
    pid = await _resolve_name_to_id("project", project_name)
    if not pid:
        return f"未找到项目「{project_name}」"
    body = {"name": name}
    color = kwargs.get("color")
    if color:
        body["color"] = color
    data = await _post(f"/tags/project/{pid}", body)
    return f"项目标签已创建！\n{_fmt_response(data)}"


async def _delete_tag(**kwargs) -> str:
    tag_id = kwargs.get("tag_id")
    if not tag_id:
        return "请提供要删除的标签ID。"
    await _delete(f"/tags/{tag_id}")
    return "标签已删除。"


async def _list_team_tags(**kwargs) -> str:
    team_name = kwargs.get("team_name")
    if not team_name:
        return "请提供团队名称。"
    tid = await _resolve_name_to_id("team", team_name)
    if not tid:
        return f"未找到团队「{team_name}」"
    data = await _get(f"/tags/team/{tid}")
    return f"团队「{team_name}」标签：\n{_fmt_response(data)}"


async def _create_team_tag(**kwargs) -> str:
    team_name = kwargs.get("team_name")
    name = kwargs.get("name")
    if not team_name or not name:
        return "请提供团队名称和标签名称。"
    tid = await _resolve_name_to_id("team", team_name)
    if not tid:
        return f"未找到团队「{team_name}」"
    body = {"teamId": tid, "name": name}
    color = kwargs.get("color")
    if color:
        body["color"] = color
    data = await _post("/tags/team", body)
    return f"团队标签已创建！\n{_fmt_response(data)}"


async def _delete_team_tag(**kwargs) -> str:
    team_name = kwargs.get("team_name")
    name = kwargs.get("name")
    if not team_name or not name:
        return "请提供团队名称和标签名称。"
    tid = await _resolve_name_to_id("team", team_name)
    if not tid:
        return f"未找到团队「{team_name}」"
    data = await _get(f"/tags/team/{tid}")
    items = data if isinstance(data, list) else []
    tag_id = None
    for t in items:
        if isinstance(t, dict) and t.get("name") == name:
            tag_id = t.get("id")
            break
    if not tag_id:
        return f"未找到标签「{name}」"
    await _delete(f"/tags/{tag_id}")
    return "标签已删除。"


async def _add_tag_to_todo(**kwargs) -> str:
    keyword = kwargs.get("keyword")
    tag_id = kwargs.get("tag_id")
    if not keyword or not tag_id:
        return "请提供任务关键词和标签ID。"
    todo_id, err = await _search_todo_by_keyword(keyword)
    if err:
        return err
    await _post(f"/tags/todo/{todo_id}", {"tagId": tag_id})
    return "标签已添加到任务。"


async def _remove_tag_from_todo(**kwargs) -> str:
    keyword = kwargs.get("keyword")
    tag_id = kwargs.get("tag_id")
    if not keyword or not tag_id:
        return "请提供任务关键词和标签ID。"
    todo_id, err = await _search_todo_by_keyword(keyword)
    if err:
        return err
    await _delete(f"/tags/todo/{todo_id}/{tag_id}")
    return "标签已从任务移除。"


async def _get_todo_tags(**kwargs) -> str:
    keyword = kwargs.get("keyword")
    if not keyword:
        return "请提供任务关键词。"
    todo_id, err = await _search_todo_by_keyword(keyword)
    if err:
        return err
    data = await _get(f"/tags/todo/{todo_id}")
    return f"任务标签：\n{_fmt_response(data)}"


# ==================== 辅助 (11) ====================

async def _get_todo(**kwargs) -> str:
    keyword = kwargs.get("keyword")
    if not keyword:
        return "请提供要查询的任务关键词。"
    todo_id, err = await _search_todo_by_keyword(keyword)
    if err:
        return err
    data = await _get(f"/todos/{todo_id}")
    return _fmt_response_dict(data)


async def _get_todo_by_id(**kwargs) -> str:
    todo_id = kwargs.get("todo_id")
    if not todo_id:
        return "请提供任务ID。"
    data = await _get(f"/todos/{todo_id}")
    return _fmt_response_dict(data)


async def _get_project_by_id(**kwargs) -> str:
    project_id = kwargs.get("project_id")
    if not project_id:
        return "请提供项目ID。"
    data = await _get(f"/projects/{project_id}")
    return _fmt_response_dict(data)


async def _get_team_by_id(**kwargs) -> str:
    team_id = kwargs.get("team_id")
    if not team_id:
        return "请提供团队ID。"
    data = await _get(f"/teams/{team_id}")
    return _fmt_response_dict(data)


async def _query_todos_by_project(**kwargs) -> str:
    project_name = kwargs.get("project_name")
    if not project_name:
        return "请提供项目名称。"
    pid = await _resolve_name_to_id("project", project_name)
    if not pid:
        return f"未找到项目「{project_name}」"
    params = {"projectId": pid}
    if kwargs.get("section_name"):
        sid = await _resolve_section_name(pid, kwargs["section_name"])
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
    data = await _get("/todos/by-project", params)
    return _format_todo_list(data)


async def _move_todo_section(**kwargs) -> str:
    keyword = kwargs.get("keyword")
    section_name = kwargs.get("section_name")
    if not keyword or not section_name:
        return "请提供要移动的任务关键词和目标分区名称。"
    todo_id, err = await _search_todo_by_keyword(keyword)
    if err:
        return err
    todo_data = await _get(f"/todos/{todo_id}")
    project_id = todo_data.get("projectId") if isinstance(todo_data, dict) else None
    if not project_id:
        return "该任务没有关联项目，无法移动分区。"
    sid = await _resolve_section_name(project_id, section_name)
    if not sid:
        return f"项目中没有找到名为「{section_name}」的分区。"
    data = await _patch(f"/todos/{todo_id}/move-section", {"sectionId": sid})
    return f"任务已移动到「{section_name}」分区！\n{_fmt_response(data)}"


async def _reorder_todos(**kwargs) -> str:
    ordered = kwargs.get("ordered_keywords")
    if not ordered or not isinstance(ordered, list) or not ordered:
        return "请提供排序后的任务关键词列表。"
    items = []
    for idx, keyword in enumerate(ordered):
        todo_id, err = await _search_todo_by_keyword(keyword)
        if err:
            return f"找不到任务「{keyword}」：{err}"
        items.append({"id": todo_id, "sortOrder": idx})
    await _put("/todos/reorder", items)
    return "排序已更新。"


async def _import_todos(**kwargs) -> str:
    project_name = kwargs.get("project_name")
    tasks = kwargs.get("tasks")
    if not project_name or not tasks:
        return "请提供项目名称和任务列表。"
    pid = await _resolve_name_to_id("project", project_name)
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
    data = await _post(f"/todos/import/{pid}", items)
    return f"成功导入 {len(items)} 条任务！\n{_fmt_response(data)}"
