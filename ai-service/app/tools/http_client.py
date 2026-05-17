"""HTTP 请求工具 —— 封装对 Java 后端 API 的调用。

关键设计点：
- 通过 get_tool_settings() 从单例读取配置，不再需要 settings 参数
- _request 先解析 JSON 再 raise_for_status：后端返回的错误消息在 JSON body 中，
  先解析 body 可提取业务错误信息，而非仅显示通用 HTTP 状态码
- 所有请求 30 秒超时，无重试（工具调用失败由 Tool Loop 的异常处理兜底）
"""

import httpx

from app.tools.tool_settings import get_tool_settings
from app.tools.context import scope_token


def _base() -> str:
    return get_tool_settings().api_base_url


def _headers() -> dict:
    settings = get_tool_settings()
    h = {"Content-Type": "application/json"}
    # 优先使用请求级 scope_token（由 chat 端点从 request.token 注入），
    # 回退到全局 scope_token 配置
    token = scope_token.get() or settings.scope_token
    if token:
        prefix = settings.api_auth_token_prefix.rstrip(" ")
        if not token.startswith(prefix + " "):
            token = f"{prefix} {token}"
        h[settings.api_auth_header] = token
    return h


async def _request(method: str, path: str, json_data: dict = None, params: dict = None) -> dict:
    """核心 HTTP 请求方法。

    为什么先解析 body 再 raise_for_status：
    Java 后端在错误响应中通过 JSON body 返回业务错误消息，
    先提取 body 再检查状态码可以拿到具体的 msg 而非仅 "500 Internal Server Error"。
    """
    async with httpx.AsyncClient(timeout=30) as client:
        resp = await client.request(
            method, f"{_base()}{path}",
            json=json_data, params=params,
            headers=_headers(),
        )
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
    # 统一响应格式：code 200/0 表示成功，提取 data 字段
    if isinstance(body, dict) and body.get("code") in (200, 0):
        return body.get("data", body)
    if isinstance(body, dict):
        raise RuntimeError(body.get("message", f"API 返回错误 (code={body.get('code')})"))
    return body


async def _get(path: str, params: dict = None) -> dict:
    return await _request("GET", path, params=params)


async def _post(path: str, json_data: dict = None) -> dict:
    return await _request("POST", path, json_data=json_data)


async def _put(path: str, json_data: dict = None) -> dict:
    return await _request("PUT", path, json_data=json_data)


async def _patch(path: str, json_data: dict = None) -> dict:
    return await _request("PATCH", path, json_data=json_data)


async def _delete(path: str) -> dict:
    return await _request("DELETE", path)
