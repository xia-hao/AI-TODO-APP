import time
import uuid
from typing import Any


class SessionManager:
    """会话管理器 —— 纯内存存储，重启后数据丢失。

    系统提示词由外部注入（来自 config.Settings.system_prompt），
    保证单点维护，避免多处硬编码不一致。
    """

    def __init__(self, system_prompt: str = ""):
        self._sessions: dict[str, dict[str, Any]] = {}
        self._system_prompt = system_prompt

    def create_session(self) -> str:
        session_id = uuid.uuid4().hex
        self._sessions[session_id] = {
            "messages": [
                {"role": "system", "content": self._system_prompt}
            ],
            "created_at": time.time(),
        }
        return session_id

    def get_history(self, session_id: str) -> list:
        session = self._sessions.get(session_id)
        if session is None:
            return []
        return session["messages"]

    def add_message(self, session_id: str, role: str, content: str, **extra):
        """追加消息到会话历史。会话不存在时自动创建。

        为什么自动创建：支持无状态模式 —— 请求中可能携带 DB 预加载的消息列表，
        但系统提示词仍需在会话中初始化。
        """
        session = self._sessions.get(session_id)
        if session is None:
            session = {
                "messages": [
                    {"role": "system", "content": self._system_prompt}
                ],
                "created_at": time.time(),
            }
            self._sessions[session_id] = session
        msg = {"role": role, "content": content}
        msg.update(extra)
        session["messages"].append(msg)

    def clear_session(self, session_id: str):
        session = self._sessions.get(session_id)
        if session:
            session["messages"] = [
                msg for msg in session["messages"] if msg["role"] == "system"
            ]

    def cleanup_old(self, max_age_hours: int = 24):
        cutoff = time.time() - max_age_hours * 3600
        expired = [
            sid
            for sid, sess in self._sessions.items()
            if sess.get("created_at", 0) < cutoff
        ]
        for sid in expired:
            del self._sessions[sid]
