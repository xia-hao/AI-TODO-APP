import asyncio
import json
import logging
import time
from dataclasses import dataclass

from fastapi import APIRouter
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

from app.config import Settings
from app.knowledge import KnowledgeStore
from app.llm.openai_compat import OpenAICompatClient
from app.session.manager import SessionManager
from app.tools.registry import ToolRegistry
from app.tools.context import scope_token

logger = logging.getLogger(__name__)

router = APIRouter()

# 最大工具调用轮次
_MAX_TOOL_ITERATIONS = 5

# 等待用户确认的超时时间（秒）
_CONFIRM_TIMEOUT = 60

# ── 人类确认：写操作（增删改）需要用户先确认再执行 ──
_WRITE_TOOLS = {
    # 任务
    "create_todo", "update_todo", "complete_todo", "delete_todo",
    "restore_todo", "delete_todo_permanent", "move_todo_section",
    "reorder_todos", "import_todos",
    # 项目
    "create_project", "update_project", "delete_project",
    "create_section", "update_section", "delete_section", "reorder_sections",
    # 团队
    "create_team", "update_team", "delete_team",
    "add_team_member", "remove_team_member", "join_team", "update_member_role",
    # 子任务
    "create_subtask", "update_subtask", "complete_subtask", "delete_subtask",
    # 标签
    "create_project_tag", "create_team_tag", "delete_tag", "delete_team_tag",
    "add_tag_to_todo", "remove_tag_from_todo",
    # 评论
    "create_comment", "delete_comment",
}

# 用于 SSE 的异步确认：event_generator 等待前端确认信号
_pending_confirm_events: dict[str, asyncio.Event] = {}
_pending_confirm_results: dict[str, bool] = {}
_pending_confirm_tokens: dict[str, str] = {}  # 确认时刷新 token，防止长时间对话过期

# ── 规划能力提示词：注入 system prompt，让 LLM 先计划再执行 ──
_PLANNING_PROMPT = (
    "## 执行要求\n"
    "对于复杂请求（涉及多个步骤或多个工具调用），请先输出执行计划，"
    "说明你要分几步完成，然后按计划逐步调用工具。"
    "每一步完成后根据结果决定下一步。"
)

# 工具执行时的用户可见提示
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
    "list_sections": "正在获取分区列表...",
    "create_section": "正在创建分区...",
    "update_section": "正在更新分区...",
    "delete_section": "正在删除分区...",
    "reorder_sections": "正在调整分区顺序...",
    "create_team": "正在创建团队...",
    "update_team": "正在更新团队...",
    "delete_team": "正在解散团队...",
    "list_teams": "正在获取团队列表...",
    "list_team_members": "正在获取团队成员...",
    "add_team_member": "正在添加团队成员...",
    "remove_team_member": "正在移除团队成员...",
    "join_team": "正在加入团队...",
    "update_member_role": "正在更新成员角色...",
    "create_subtask": "正在创建子任务...",
    "update_subtask": "正在更新子任务...",
    "complete_subtask": "正在切换子任务状态...",
    "delete_subtask": "正在删除子任务...",
    "list_subtasks": "正在获取子任务列表...",
    "get_statistics": "正在获取统计数据...",
    "generate_report": "正在生成报告...",
    "get_calendar_events": "正在获取日历事件...",
    "move_todo_section": "正在移动任务分区...",
    "list_project_tags": "正在获取项目标签...",
    "create_project_tag": "正在创建项目标签...",
    "list_team_tags": "正在获取团队标签...",
    "create_team_tag": "正在创建团队标签...",
    "delete_tag": "正在删除标签...",
    "delete_team_tag": "正在删除团队标签...",
    "add_tag_to_todo": "正在添加标签...",
    "remove_tag_from_todo": "正在移除标签...",
    "get_todo_tags": "正在获取标签...",
    "list_comments": "正在获取评论...",
    "create_comment": "正在添加评论...",
    "delete_comment": "正在删除评论...",
    "query_todos_by_project": "正在按项目查询任务...",
    "get_todo": "正在获取任务详情...",
    "restore_todo": "正在恢复任务...",
    "delete_todo_permanent": "正在永久删除任务...",
    "list_deleted_todos": "正在获取已删除任务...",
    "get_todo_by_id": "正在查找任务...",
    "get_project_by_id": "正在查找项目...",
    "get_team_by_id": "正在查找团队...",
    "reorder_todos": "正在调整任务排序...",
    "import_todos": "正在导入任务...",
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
    knowledge_store: KnowledgeStore | None = None


_deps: AppDeps = AppDeps()


class ChatRequest(BaseModel):
    session_id: str = ""
    message: str
    token: str = ""
    llm_provider: str | None = None
    username: str = ""
    display_name: str = ""
    messages: list[dict] | None = None  # 从 DB 预加载的历史消息（无状态模式）


class ConfirmActionRequest(BaseModel):
    """用户对写操作的确认/拒绝请求。

    前端展示确认对话框后，用户点击确认或拒绝时调用 /chat/confirm-action。
    """
    confirm_id: str     # 由 event_generator 生成的唯一确认 ID
    approved: bool      # True=确认执行, False=拒绝
    token: str = ""     # 刷新后的 JWT token（可选，解决长时间对话 token 过期）


class GenerateTitleRequest(BaseModel):
    message: str


def setup_deps(settings: Settings, tool_registry: ToolRegistry | None = None, knowledge_store: KnowledgeStore | None = None) -> AppDeps:
    """初始化并注入所有依赖。在 app 启动时由 main.py 调用一次。"""
    _deps.settings = settings
    _deps.llm_client = OpenAICompatClient(settings)
    _deps.session_mgr = SessionManager(system_prompt=settings.system_prompt)
    _deps.tool_registry = tool_registry or ToolRegistry()
    _deps.knowledge_store = knowledge_store
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


def _ks() -> KnowledgeStore | None:
    """获取知识库实例。"""
    return _deps.knowledge_store


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

    # 构建系统提示补充信息（用户身份 + 规划要求）
    system_suffix = "\n" + _PLANNING_PROMPT
    if request.username:
        system_suffix += (
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
            messages[0]["content"] = settings.system_prompt + system_suffix  # 替换旧提示词
        else:
            messages.insert(0, {"role": "system", "content": settings.system_prompt + system_suffix})  # 插入新提示词
    else:
        # 有状态模式：追加用户消息到内存会话
        session_mgr.add_message(session_id, "user", request.message)
        if system_suffix:
            history = session_mgr.get_history(session_id)
            if history and history[0].get("role") == "system" and system_suffix not in history[0].get("content", ""):
                history[0]["content"] += system_suffix
        messages = list(session_mgr.get_history(session_id))

    # ── 知识注入：检索进化器提取的业务规则，作为 system 参考 ──
    # 这些规则由 Evolver 定期从对话中提炼，非原始对话原文。
    knowledge_store = _ks()
    if knowledge_store is not None:
        try:
            results = await knowledge_store.search(request.message, top_k=3)
            if results:
                rules = []
                for r in results:
                    rules.append(f"- {r.entry.content}")
                messages.insert(1, {
                    "role": "system",
                    "content": "## 经验规则\n" + "\n".join(rules),
                })
        except Exception as e:
            logger.warning("知识检索失败，跳过注入：%s", e)

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

                        # ── 人类确认：写操作需要用户先确认再执行 ──
                        # 前端收到 confirm 事件后展示确认对话框，用户点击后调用
                        # /api/chat/confirm-action 接口返回结果。
                        if tool_name in _WRITE_TOOLS:
                            confirm_id = f"{session_id}:{tool_name}:{time.time()}"
                            confirm_event = asyncio.Event()
                            _pending_confirm_events[confirm_id] = confirm_event

                            yield f"event: message\ndata: {json.dumps({
                                'type': 'confirm',
                                'confirm_id': confirm_id,
                                'tool': tool_name,
                                'hint': hint,
                                'args': tool_args,
                            }, ensure_ascii=False)}\n\n"

                            try:
                                await asyncio.wait_for(confirm_event.wait(), timeout=_CONFIRM_TIMEOUT)
                                approved = _pending_confirm_results.pop(confirm_id, False)
                                # 刷新 token：确认时可能传入了新 token，给后续工具调用使用
                                new_token = _pending_confirm_tokens.pop(confirm_id, None)
                                if new_token:
                                    scope_token.set(new_token)
                            except asyncio.TimeoutError:
                                approved = False
                            finally:
                                _pending_confirm_events.pop(confirm_id, None)
                                _pending_confirm_results.pop(confirm_id, None)
                                _pending_confirm_tokens.pop(confirm_id, None)

                            if not approved:
                                logger.info("用户取消操作: %s", confirm_id)
                                result = "操作已被用户取消。请告知用户操作未执行。"
                                # 跳过工具执行，直接进入结果处理
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
                                continue

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


@router.post("/chat/confirm-action")
async def confirm_action(request: ConfirmActionRequest):
    """用户确认/拒绝写操作。

    当 event_generator 中遇到写工具（增删改）时，会 yield 一个 confirm 事件，
    前端展示确认对话框。用户操作后调用此接口回复确认结果，
    event_generator 中的等待协程收到信号后继续执行或取消。
    """
    confirm_id = request.confirm_id
    logger.info("收到用户确认: %s approved=%s", confirm_id, request.approved)

    event = _pending_confirm_events.get(confirm_id)
    if event is None:
        return {"status": "not_found", "message": "确认请求已过期或不存在"}

    # 保存确认结果 + 刷新后的 token
    _pending_confirm_results[confirm_id] = request.approved
    if request.token:
        _pending_confirm_tokens[confirm_id] = request.token
    event.set()
    return {"status": "ok", "approved": request.approved}
