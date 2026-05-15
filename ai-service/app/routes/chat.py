import asyncio
import json
import logging
from typing import Any

from fastapi import APIRouter
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

from app.config import Settings
from app.llm.openai_compat import OpenAICompatClient
from app.session.manager import SessionManager
from app.tools.registry import ToolRegistry
from app.tools.context import scope_token

logger = logging.getLogger(__name__)

router = APIRouter()

_MAX_TOOL_ITERATIONS = 5

_TOOL_HINTS = {
    "create_todo": "正在创建待办...",
    "update_todo": "正在更新待办...",
    "complete_todo": "正在切换待办状态...",
    "delete_todo": "正在删除待办...",
    "search_todos": "正在搜索任务...",
    "create_project": "正在创建项目...",
    "update_project": "正在更新项目...",
    "delete_project": "正在删除项目...",
    "list_projects": "正在获取项目列表...",
    "create_team": "正在创建团队...",
    "update_team": "正在更新团队...",
    "delete_team": "正在解散团队...",
    "list_teams": "正在获取团队列表...",
    "list_team_members": "正在获取团队成员...",
    "create_subtask": "正在创建子任务...",
    "update_subtask": "正在更新子任务...",
    "complete_subtask": "正在切换子任务状态...",
    "delete_subtask": "正在删除子任务...",
    "get_statistics": "正在获取统计数据...",
    "generate_report": "正在生成报告...",
    "get_calendar_events": "正在获取日历事件...",
    "move_todo_section": "正在移动任务分区...",
}

_deps: dict[str, Any] = {}


class ChatRequest(BaseModel):
    session_id: str = ""
    message: str
    token: str = ""
    llm_provider: str | None = None
    username: str = ""
    display_name: str = ""
    messages: list[dict] | None = None  # optional pre-loaded history (from DB)


class GenerateTitleRequest(BaseModel):
    message: str


def setup_deps(settings: Settings) -> dict:
    _deps["settings"] = settings
    _deps["llm_client"] = OpenAICompatClient(settings)
    _deps["session_mgr"] = SessionManager()
    _deps["tool_registry"] = ToolRegistry()
    return _deps


def _s() -> Settings:
    return _deps["settings"]


def _llm() -> OpenAICompatClient:
    return _deps["llm_client"]


def _mgr() -> SessionManager:
    return _deps["session_mgr"]


def _tools() -> ToolRegistry:
    return _deps["tool_registry"]


@router.post("/chat")
async def chat(request: ChatRequest):
    if request.token:
        scope_token.set(request.token)

    settings = _s()
    session_mgr = _mgr()
    llm_client = _llm()
    tool_registry = _tools()

    session_id = request.session_id or session_mgr.create_session()

    # Build the user identity suffix for system prompt
    user_info = ""
    if request.username:
        user_info = (
            f"\n当前用户信息：用户名={request.username}，显示名称={request.display_name or request.username}。"
            f"在回答中可以直接称呼用户的名字。"
        )

    # If messages provided from DB, use them as the base context (stateless).
    # DB history already includes the current user message, so no append needed.
    # Strip out stale tool_call/tool messages that would confuse the LLM.
    if request.messages:
        raw_messages = list(request.messages)
        messages = [
            m for m in raw_messages
            if m["role"] != "tool"
            and not (m["role"] == "assistant" and "tool_calls" in m)
        ]
        if not messages or messages[0].get("role") != "system":
            messages.insert(0, {"role": "system", "content": settings.system_prompt + user_info})
        elif user_info and user_info not in messages[0].get("content", ""):
            messages[0]["content"] += user_info
    else:
        session_mgr.add_message(session_id, "user", request.message)
        # Inject user info into session's system prompt if needed
        if user_info:
            history = session_mgr.get_history(session_id)
            if history and history[0].get("role") == "system" and user_info not in history[0].get("content", ""):
                history[0]["content"] += user_info
        messages = list(session_mgr.get_history(session_id))

    # Trim: keep system prompt + last N turns to avoid context overflow
    _MAX_HISTORY_TURNS = 10
    if len(messages) > 1:
        sys_msg = messages[0] if messages[0].get("role") == "system" else None
        rest = messages[1:] if sys_msg else messages
        if len(rest) > _MAX_HISTORY_TURNS * 2:
            rest = rest[-(_MAX_HISTORY_TURNS * 2):]
            messages = [sys_msg] + rest if sys_msg else rest

    async def event_generator():
        # Re-set scope_token here because event_generator runs in a different
        # asyncio task (driven by StreamingResponse) where the contextvar
        # set in chat() is not visible.
        if request.token:
            scope_token.set(request.token)
        full_response = ""
        tool_iterations = 0
        tool_was_called = False

        try:
            while tool_iterations < _MAX_TOOL_ITERATIONS:
                tool_iterations += 1
                has_tool_call = False
                iteration_text = ""
                iteration_reasoning = ""

                tool_calls_batch: list[dict] = []

                for chunk in llm_client.chat_stream(messages, tool_registry.get_tool_defs()):
                    ctype = chunk.get("type")

                    if ctype == "text":
                        content = chunk.get("content", "")
                        iteration_text += content
                        yield f"event: message\ndata: {json.dumps(chunk, ensure_ascii=False)}\n\n"

                    elif ctype == "tool_call":
                        has_tool_call = True
                        tool_calls_batch.append(chunk)

                    elif ctype == "done":
                        done_content = chunk.get("content", "")
                        done_reasoning = chunk.get("reasoning_content", "") or ""
                        if done_content:
                            iteration_text = done_content
                        if done_reasoning:
                            iteration_reasoning = done_reasoning
                        if not has_tool_call:
                            full_response = iteration_text

                # Process collected tool calls after the stream ends
                if has_tool_call:
                    tool_was_called = True
                    tool_calls_msg: list[dict] = []
                    for tc_chunk in tool_calls_batch:
                        tool_name = tc_chunk.get("name", "")
                        tool_args = tc_chunk.get("arguments", {})
                        tool_id = tc_chunk.get("id", "")
                        tc_reasoning = tc_chunk.get("reasoning_content", "") or ""
                        if tc_reasoning:
                            iteration_reasoning = tc_reasoning

                        hint = _TOOL_HINTS.get(tool_name, "处理中...")
                        yield f"event: message\ndata: {json.dumps({'type': 'thinking', 'hint': hint}, ensure_ascii=False)}\n\n"

                        try:
                            result = await tool_registry.execute(tool_name, tool_args)
                        except asyncio.CancelledError:
                            logger.warning("Tool '%s' was cancelled (client disconnect?)", tool_name)
                            result = "操作被中断，请重试。"
                        except Exception as e:
                            logger.exception("Tool '%s' failed: %s", tool_name, e)
                            result = "操作执行失败，请稍后重试。"

                        tool_calls_msg.append({
                            "id": tool_id,
                            "type": "function",
                            "function": {
                                "name": tool_name,
                                "arguments": json.dumps(tool_args, ensure_ascii=False),
                            },
                        })
                        messages.append({
                            "role": "tool",
                            "tool_call_id": tool_id,
                            "content": result,
                        })

                    assistant_msg = {
                        "role": "assistant",
                        "content": iteration_text or None,
                        "tool_calls": tool_calls_msg,
                    }
                    if iteration_reasoning:
                        assistant_msg["reasoning_content"] = iteration_reasoning
                    messages.insert(len(messages) - len(tool_calls_batch), assistant_msg)

                    iteration_text = ""
                else:
                    break

        except asyncio.CancelledError:
            logger.warning("SSE stream cancelled (client disconnected)")
        except Exception as e:
            logger.exception("SSE stream error: %s", e)
            if not full_response:
                full_response = f"处理请求时出现异常：{e}"

        # If the LLM made tool calls but returned empty text in the final
        # iteration (no summary after tool results), force a summarization call.
        if not full_response and tool_was_called:
            messages.append({"role": "user", "content": "请根据以上所有信息，用中文给出完整的回答。"})
            for chunk in llm_client.chat_stream(messages, []):
                ctype = chunk.get("type")
                if ctype == "text":
                    full_response += chunk.get("content", "")
                    yield f"event: message\ndata: {json.dumps(chunk, ensure_ascii=False)}\n\n"
                elif ctype == "done":
                    break
            full_response = full_response.strip()

        if full_response:
            extra = {}
            if iteration_reasoning:
                extra["reasoning_content"] = iteration_reasoning
            try:
                session_mgr.add_message(session_id, "assistant", full_response, **extra)
            except Exception:
                pass

        yield f"event: done\ndata: {json.dumps({'type': 'done', 'content': full_response}, ensure_ascii=False)}\n\n"

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


@router.post("/generate-title")
async def generate_title(request: GenerateTitleRequest):
    settings = _s()
    llm_client = _llm()

    messages = [
        {"role": "system", "content": "根据用户的输入，生成一个简短的对话标题（不超过 20 个字），直接返回标题内容，不要解释。"},
        {"role": "user", "content": request.message},
    ]

    full_title = ""
    for chunk in llm_client.chat_stream(messages, []):
        if chunk.get("type") == "text":
            full_title += chunk.get("content", "")
        elif chunk.get("type") == "done":
            break

    title = full_title.strip().strip('"\'「」').strip()[:50] or "新对话"
    return {"title": title}
