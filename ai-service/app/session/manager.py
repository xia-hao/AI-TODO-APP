import time
import uuid
from typing import Any


class SessionManager:
    def __init__(self):
        self._sessions: dict[str, dict[str, Any]] = {}

    def create_session(self) -> str:
        session_id = uuid.uuid4().hex
        self._sessions[session_id] = {
            "messages": [
                {
                    "role": "system",
                    "content": (
                        "你是一个智能任务管理助手，可以帮助用户管理任务、查询统计信息、生成报告等。"
                        "你可以使用提供的工具来执行操作。请用中文回答用户的问题，回答要简洁明了。"
                    ),
                }
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
        session = self._sessions.get(session_id)
        if session is None:
            session = {
                "messages": [
                    {
                        "role": "system",
                        "content": (
                            "你是一个智能任务管理助手，可以帮助用户管理任务、查询统计信息、生成报告等。"
                            "你可以使用提供的工具来执行操作。请用中文回答用户的问题，回答要简洁明了。"
                        ),
                    }
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
