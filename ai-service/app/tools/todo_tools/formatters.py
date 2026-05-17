"""响应格式化工具 —— 将后端 JSON 响应转换为 LLM 易读的结构化中文文本。

为什么用结构化文本而不是 JSON：
LLM 对结构化中文文本的理解效率高于原始 JSON。
格式化后的输出减少了 token 消耗，同时让 LLM 更容易提取关键信息
在后续对话中引用（如任务编号、项目名称等）。
"""

import json


def _escape(s: str) -> str:
    """转义换行符，防止破坏列表格式。"""
    return s.replace("\n", " ").replace("\r", " ").strip() if s else ""


def _fmt_response(data) -> str:
    """通用响应格式化 —— 根据数据类型分发到对应格式化器。"""
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
    """单个实体格式化 —— 提取关键字段用 | 连接。"""
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
    """格式化分区列表为中文顿号连接。"""
    if not sections:
        return "（无分区）"
    return "、".join(
        s.get("name", f"分区{i+1}")
        for i, s in enumerate(sections)
    )


def _fmt_items(data: list) -> str:
    """通用列表项格式化 —— 带序号、标题和元信息标签。"""
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


def _format_todo_list(data) -> str:
    """待办列表专用格式化 —— 带完成状态图标、优先级、标签等。"""
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
        lines.append(
            f"{i}. {status} {text} [{priority}]"
            f"{f' 截止:{due}' if due else ''}"
            f"{f' 项目:{project}' if project else ''}"
            f"{f' 分区:{section}' if section else ''}"
            f"{f' 负责人:{assignee}' if assignee else ''}"
            f"{tag_str}"
        )
    if len(items) > 50:
        lines.append(f"... 还有 {len(items) - 50} 条")
    return "\n".join(lines)
