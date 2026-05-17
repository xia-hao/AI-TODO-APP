import asyncio
import json
import logging
from dataclasses import dataclass

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

# 最大工具调用轮次 —— 防止 LLM 在工具循环中无限迭代耗尽 token
_MAX_TOOL_ITERATIONS = 5

# 工具执行时的用户可见提示，避免长时间无反馈
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


# ============================================================
# 依赖注入 —— 启动时 setup_deps() 初始化，所有请求复用
# ============================================================

@dataclass
class AppDeps:
    """应用级依赖容器。每次启动时通过 setup_deps() 填充。"""
    settings: Settings | None = None
    llm_client: OpenAICompatClient | None = None
    session_mgr: SessionManager | None = None
    tool_registry: ToolRegistry | None = None


_deps: AppDeps = AppDeps()


class ChatRequest(BaseModel):
    session_id: str = ""
    message: str
    token: str = ""
    llm_provider: str | None = None
    username: str = ""
    display_name: str = ""
    messages: list[dict] | None = None  # 从 DB 预加载的历史消息（无状态模式）


class GenerateTitleRequest(BaseModel):
    message: str


def setup_deps(settings: Settings, tool_registry: ToolRegistry | None = None) -> AppDeps:
    """初始化并注入所有依赖。在 app 启动时由 main.py 调用一次。"""
    _deps.settings = settings
    _deps.llm_client = OpenAICompatClient(settings)
    _deps.session_mgr = SessionManager(system_prompt=settings.system_prompt)
    _deps.tool_registry = tool_registry or ToolRegistry()
    return _deps


def _s() -> Settings:
    assert _deps.settings is not None, "Settings 未初始化"
    return _deps.settings


def _llm() -> OpenAICompatClient:
    assert _deps.llm_client is not None, "LLMClient 未初始化"
    return _deps.llm_client


def _mgr() -> SessionManager:
    assert _deps.session_mgr is not None, "SessionManager 未初始化"
    return _deps.session_mgr


def _tools() -> ToolRegistry:
    assert _deps.tool_registry is not None, "ToolRegistry 未初始化"
    return _deps.tool_registry


@router.post("/chat")
async def chat(request: ChatRequest):
    """核心对话端点 — SSE 流式返回，支持 Tool Loop (ReAct 模式)。

    关键设计点：
    1. scope_token 仅在 event_generator 内设置 —— 因为 StreamingResponse
       会创建新的 asyncio task，ContextVar 不跨 task 传递，chat() 中设置无效。
    2. 支持两种历史模式：
       - 有状态：使用内存 SessionManager 的历史
       - 无状态：请求携带 DB 预加载的 messages，需要剥离旧的 tool_call/tool 消息
         （这些消息引用了上一轮对话的工具调用，会混淆 LLM）
    """
    settings = _s()
    session_mgr = _mgr()
    llm_client = _llm()
    tool_registry = _tools()

    session_id = request.session_id or session_mgr.create_session()

    # 构建用户身份信息注入到系统提示词
    user_info = ""
    if request.username:
        user_info = (
            f"\n当前用户信息：用户名={request.username}，显示名称={request.display_name or request.username}。"
            f"在回答中可以直接称呼用户的名字。"
        )

    # 无状态模式：使用 DB 历史。剥离 tool/tool_calls 消息避免 LLM 混淆
    if request.messages:
        raw_messages = list(request.messages)
        messages = [
            m for m in raw_messages
            if m["role"] != "tool"
            and not (m["role"] == "assistant" and "tool_calls" in m)
        ]
        # 用最新系统提示词确保工具能力描述不丢失（DB 不存 system 消息时插入，有旧版时替换）
        if messages and messages[0].get("role") == "system":
            messages[0]["content"] = settings.system_prompt + user_info  # 替换旧提示词
        else:
            messages.insert(0, {"role": "system", "content": settings.system_prompt + user_info})  # 插入新提示词
    else:
        # 有状态模式：追加用户消息到内存会话
        session_mgr.add_message(session_id, "user", request.message)
        if user_info:
            history = session_mgr.get_history(session_id)
            if history and history[0].get("role") == "system" and user_info not in history[0].get("content", ""):
                history[0]["content"] += user_info
        messages = list(session_mgr.get_history(session_id))

    # 裁剪历史：保留系统提示词 + 最近 N 轮对话，防止上下文溢出
    _MAX_HISTORY_TURNS = 10
    if len(messages) > 1:
        sys_msg = messages[0] if messages[0].get("role") == "system" else None
        rest = messages[1:] if sys_msg else messages
        if len(rest) > _MAX_HISTORY_TURNS * 2:
            rest = rest[-(_MAX_HISTORY_TURNS * 2):]
            messages = [sys_msg] + rest if sys_msg else rest

    async def event_generator():
        """SSE 事件生成器 —— 实现 Tool Loop (ReAct)。

        流程：LLM 流式输出 → 收集工具调用 → 执行工具 → 结果喂回 LLM → 循环
        最多 MAX_TOOL_ITERATIONS 轮。如果 LLM 调用了工具但最终无文本输出，
        会强制追加一条总结请求（DeepSeek 某些版本会只返回 tool_calls 无摘要）。

        为什么 scope_token 在这里重新设置：
        StreamingResponse 会启动新的 asyncio.Task，ContextVar 上下文不继承。
        """
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
                        # SST 第一层：LLM 的文本直接发给客户端
                        yield f"event: message\ndata: {json.dumps(chunk, ensure_ascii=False)}\n\n"

                    elif ctype == "tool_call":
                        # 工具调用不暴露给客户端，只收集到 batch 等待执行
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

                # 流结束后，批量处理收集到的工具调用
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

                        # SST 第二层：只发送模糊提示，不暴露工具名称和参数
                        hint = _TOOL_HINTS.get(tool_name, "处理中...")
                        yield f"event: message\ndata: {json.dumps({'type': 'thinking', 'hint': hint}, ensure_ascii=False)}\n\n"

                        # 执行工具 —— 获取的真实数据只进 LLM 上下文，不 yield 给客户端
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
                        # SST 第三层：工具结果只进入 LLM 上下文，不返回客户端
                        # 客户端最终看到的是 LLM 消化后的总结，而非原始后端数据
                        messages.append({
                            "role": "tool",
                            "tool_call_id": tool_id,
                            "content": result,
                        })

                    # assistant(tool_calls) 必须排在 tool 结果消息之前：
                    # OpenAI API 要求两条消息成对出现且顺序固定
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

        # 工具已执行但 LLM 没有返回总结文本时，强制请求总结
        # 常见于 DeepSeek 等提供商在流模式下只输出 tool_calls 无文本
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
            messages.pop()  # 用完即删，临时提示词不污染对话历史

        if full_response:
            extra = {}
            if iteration_reasoning:
                extra["reasoning_content"] = iteration_reasoning
            try:
                session_mgr.add_message(session_id, "assistant", full_response, **extra)
            except Exception:
                pass

        # SST 第四层：最终响应是 LLM 消化工具结果后的总结，
        # 不包含任何原始后端数据
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
