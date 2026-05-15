import json
import re
from typing import Generator

from openai import OpenAI

from app.config import Settings
from app.llm.base import LLMClient

# Detect Anthropic/DeepSeek-style XML tool calls emitted as text content
# by some providers that don't support structured tool_calls in streaming.
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


def _parse_xml_tool_args(xml_body: str) -> dict:
    """Parse Anthropic-style XML parameter blocks into a dict."""
    args = {}
    for m in _XML_PARAM_RE.finditer(xml_body):
        name = m.group(1)
        # Strip string=true/false, number=* type annotations — only keep the text content
        value = m.group(2).strip()
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

                # Buffer text content — we may need to discard it if it's an XML tool call
                if delta.content:
                    full_content += delta.content
                    text_buffer.append(delta.content)
                    continue

                if delta.tool_calls:
                    # Standard OpenAI tool call — flush buffer (preamble text), then handle
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
                    # Check if the accumulated content is an XML-style tool call
                    xml_tc_match = _XML_TOOL_CALLS_RE.search(full_content)
                    if xml_tc_match:
                        xml_block = xml_tc_match.group(1)
                        invokes = _XML_INVOKE_RE.findall(xml_block)
                        if invokes:
                            text_buffer = []  # discard XML text — don't show to user
                            full_content = ""  # don't pass XML to done event
                            done_yielded = True  # suppress trailing done from finally
                            for tool_name, xml_body in invokes:
                                tool_args = _parse_xml_tool_args(xml_body)
                                yield {
                                    "type": "tool_call",
                                    "id": "xml-toolcall",
                                    "name": tool_name,
                                    "arguments": tool_args,
                                    "reasoning_content": full_reasoning,
                                }
                            continue  # skip yielding done — event loop will re-enter
                    else:
                        # Normal text response — flush buffered text, then done
                        for t in _flush_text():
                            yield t
                        if not done_yielded:
                            done_yielded = True
                            yield {"type": "done", "content": full_content, "reasoning_content": full_reasoning}

        except Exception as e:
            logger = __import__("logging").getLogger(__name__)
            logger.warning("LLM stream interrupted: %s", e)
        finally:
            if not done_yielded:
                yield {"type": "done", "content": full_content, "reasoning_content": full_reasoning}

    def _build_tool_defs(self, tools: list) -> list:
        result = []
        for tool in tools:
            if isinstance(tool, dict):
                # Tool def may already include "type": "function" wrapper
                if "type" in tool and "function" in tool:
                    result.append(tool)
                else:
                    result.append({"type": "function", "function": tool})
            else:
                # Assume it's an object with a get_tool_def() method
                result.append(tool.get_tool_def())
        return result
