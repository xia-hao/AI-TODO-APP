"""API Key 验证中间件。

为什么空 key 返回 500 而不是 401：
空 key 表示服务端未配置 INTERNAL_API_KEY，是运维配置错误而非客户端问题。
返回 500 强制服务端操作者修复配置，避免无认证运行的安全风险。
"""

from fastapi.responses import JSONResponse


async def verify_api_key(request, internal_api_key: str):
    """校验 X-Api-Key 请求头。key 为空时拒绝（禁止无密钥运行），不匹配时返回 401。"""
    if not internal_api_key:
        return JSONResponse(status_code=500, content={"detail": "Internal API key not configured"})
    api_key = request.headers.get("X-Api-Key")
    if api_key != internal_api_key:
        return JSONResponse(status_code=401, content={"detail": "Invalid API key"})
    return None
