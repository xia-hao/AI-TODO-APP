from fastapi.responses import JSONResponse


async def verify_api_key(request, internal_api_key: str):
    """校验 X-Api-Key 请求头。key 为空时拒绝（禁止无密钥运行），不匹配时返回 401。"""
    if not internal_api_key:
        return JSONResponse(status_code=500, content={"detail": "Internal API key not configured"})
    api_key = request.headers.get("X-Api-Key")
    if api_key != internal_api_key:
        return JSONResponse(status_code=401, content={"detail": "Invalid API key"})
    return None
