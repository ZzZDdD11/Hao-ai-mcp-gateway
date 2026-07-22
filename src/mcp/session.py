"""SSE 会话管理：创建、校验、清理。"""
from __future__ import annotations

import time
import uuid

from src.db import get_conn

SESSION_TIMEOUT = 1800  # 30 分钟


def create_session(gateway_id: str) -> str:
    """创建会话，返回 sessionId (UUID4)。"""
    session_id = str(uuid.uuid4())
    now = time.time()
    conn = get_conn()
    conn.execute(
        "INSERT INTO sessions (session_id, gateway_id, created_at, last_active) VALUES (?, ?, ?, ?)",
        (session_id, gateway_id, now, now),
    )
    conn.commit()
    conn.close()
    return session_id


def validate_session(session_id: str) -> bool:
    """校验 session 是否有效（存在且未超时），有效则刷新 last_active。"""
    conn = get_conn()
    row = conn.execute(
        "SELECT created_at FROM sessions WHERE session_id = ?",
        (session_id,),
    ).fetchone()
    if row is None:
        conn.close()
        return False
    if time.time() - row["created_at"] > SESSION_TIMEOUT:
        conn.close()
        return False
    conn.execute(
        "UPDATE sessions SET last_active = ? WHERE session_id = ?",
        (time.time(), session_id),
    )
    conn.commit()
    conn.close()
    return True


def cleanup_expired_sessions() -> int:
    """清理超时 session，返回删除条数。"""
    deadline = time.time() - SESSION_TIMEOUT
    conn = get_conn()
    cursor = conn.execute(
        "DELETE FROM sessions WHERE last_active < ?",
        (deadline,),
    )
    conn.commit()
    deleted = cursor.rowcount
    conn.close()
    return deleted
