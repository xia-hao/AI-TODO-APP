"""OpenAI API 兼容的流式 LLM 客户端。

关键设计：XML 工具调用回退
──────────────────────────────
部分 OpenAI 兼容提供商（Ollama、部分 DeepSeek 配置）在流模式下不通过
标准 tool_calls delta 字段返回工具调用，而是将 <tool_calls> XML 嵌入
到 content 文本中。本模块提供文本缓冲 + 正则检测的回退机制：

1. 流式 chunk 中的 delta.content 先存入 text_buffer，暂不 yield
2. 当 finish_reason == "stop" 时，检查全文是否匹配 <tool_calls> 模式
3. 如果是 XML 工具调用 → 丢弃 text_buffer，yield tool_call 事件
4. 如果不是 → flush text_buffer，yield 正常 text 事件

为什么先缓冲再判断：
流模式下内容逐块到达，在第一个 content chunk 到达时无法确定整段内容
是否为 XML 工具调用。必须等流结束后才能做出判断。

为什么在 finally 中兜底 yield done：
确保即使 LLM 流异常中断，上层 event_generator 也能收到 done 事件退出循环。
"""

import json
import logging
import re
from typing import Generator

from openai import OpenAI

from app.config import Settings
from app.llm.base import LLMClient

# 检测 Anthropic/DeepSeek 风格 XML 工具调用（嵌入在 content 文本中）
_XML_TOOL_CALLS_RE = re.compile(
    r'<tool_calls>(.*?)</tool_calls>',
    re.DOTALL,
)
_XML_INVOKE_RE = re.compile(
    r'<invoke\s+name="([^"]+)">(.*?)</invoke>',
    re.DOTALL,
)
_XML_PARAM_RE = re.compile(
    r'<parameter\s+name="([^"]+)"[^>]*>(.*?)</parameter>',
    re.DOTALL,
)

logger = logging.getLogger(__name__)


def _parse_xml_tool_args(xml_body: str) -> dict:
    """解析 XML 参数块为 dict，自动推断类型（数字/布尔/字符串）。"""
    args = {}
    for m in _XML_PARAM_RE.finditer(xml_body):
        name = m.group(1)
        value = m.group(2).strip()
        # 尝试 JSON 解析以保留数字和布尔类型，失败则保留字符串
        try:
            args[name] = json.loads(value)
        except (json.JSONDecodeError, ValueError):
            args[name] = value
    return args


class OpenAICompatClient(LLMClient):
    def __init__(self, settings: Settings):
        self.settings = settings
        self.client = OpenAI(
            api_key=settings.llm_api_key or "sk-placeholder",
            base_url=settings.llm_base_url,
        )

    def chat_stream(
        self, messages: list, tools: list, **kwargs
    ) -> Generator[dict, None, None]:
        tool_defs = self._build_tool_defs(tools) if tools else None

        try:
            stream = self.client.chat.completions.create(
                model=self.settings.llm_model,
                messages=messages,
                tools=tool_defs,
                stream=True,
                **kwargs,
            )
        except Exception as e:
            yield {"type": "text", "content": f"调用 LLM API 失败：{e}"}
            yield {"type": "done", "content": ""}
            return

        full_content = ""
        full_reasoning = ""
        tool_calls_accumulator: dict[int, dict] = {}
        done_yielded = False
        text_buffer: list[str] = []

        def _flush_text():
            nonlocal text_buffer
            for t in text_buffer:
                yield {"type": "text", "content": t}
            text_buffer = []

        try:
            for chunk in stream:
                delta = chunk.choices[0].delta if chunk.choices else None
                if delta is None:
                    continue

                if getattr(delta, "reasoning_content", None):
                    full_reasoning += delta.reasoning_content

                # 缓冲文本内容 —— 如果是 XML 工具调用则需要丢弃
                if delta.content:
                    full_content += delta.content
                    text_buffer.append(delta.content)
                    continue

                if delta.tool_calls:
                    # 标准 OpenAI 工具调用 —— 先 flush 缓冲的前导文本，再处理工具调用
                    for t in _flush_text():
                        yield t
                    for tc in delta.tool_calls:
                        idx = tc.index
                        if idx not in tool_calls_accumulator:
                            tool_calls_accumulator[idx] = {
                                "id": tc.id or "",
                                "name": "",
                                "arguments": "",
                            }
                        acc = tool_calls_accumulator[idx]
                        if tc.id:
                            acc["id"] = tc.id
                        if tc.function:
                            if tc.function.name:
                                acc["name"] += tc.function.name
                            if tc.function.arguments:
                                acc["arguments"] += tc.function.arguments

                if chunk.choices[0].finish_reason == "tool_calls":
                    for acc in tool_calls_accumulator.values():
                        try:
                            args = json.loads(acc["arguments"])
                        except (json.JSONDecodeError, KeyError):
                            args = {}
                        yield {
                            "type": "tool_call",
                            "id": acc["id"],
                            "name": acc["name"],
                            "arguments": args,
                            "reasoning_content": full_reasoning,
                        }

                if chunk.choices[0].finish_reason == "stop":
                    # 检查累积内容是否为 XML 格式的工具调用
                    xml_tc_match = _XML_TOOL_CALLS_RE.search(full_content)
                    if xml_tc_match:
                        xml_block = xml_tc_match.group(1)
                        invokes = _XML_INVOKE_RE.findall(xml_block)
                        if invokes:
                            # 保留 XML 块之前的前导文本（如"好的，我来查一下"），只丢弃 XML 块
                            before_xml = full_content[:xml_tc_match.start()]
                            if before_xml.strip():
                                text_buffer = [before_xml]
                                for t in _flush_text():
                                    yield t
                            else:
                                text_buffer = []
                            full_content = before_xml if before_xml.strip() else ""
                            done_yielded = True  # 抑制 finally 中的兜底 done
                            for idx, (tool_name, xml_body) in enumerate(invokes):
                                tool_args = _parse_xml_tool_args(xml_body)
                                yield {
                                    "type": "tool_call",
                                    "id": f"xml-toolcall-{idx}",
                                    "name": tool_name,
                                    "arguments": tool_args,
                                    "reasoning_content": full_reasoning,
                                }
                            continue
                    else:
                        # 普通文本响应 —— 输出缓冲文本，结束
                        for t in _flush_text():
                            yield t
                        if not done_yielded:
                            done_yielded = True
                            yield {"type": "done", "content": full_content, "reasoning_content": full_reasoning}

        except Exception as e:
            logger.warning("LLM stream interrupted: %s", e)
        finally:
            if not done_yielded:
                yield {"type": "done", "content": full_content, "reasoning_content": full_reasoning}

    def _build_tool_defs(self, tools: list) -> list:
        result = []
        for tool in tools:
            if isinstance(tool, dict):
                # 工具定义可能已包含 "type": "function" 外层包装
                if "type" in tool and "function" in tool:
                    result.append(tool)
                else:
                    result.append({"type": "function", "function": tool})
            else:
                # 假设是有 get_tool_def() 方法的对象
                result.append(tool.get_tool_def())
        return result
