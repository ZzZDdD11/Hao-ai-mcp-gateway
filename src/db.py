"""SQLite 数据库管理：建表、连接。"""
from __future__ import annotations

import sqlite3
from pathlib import Path

DB_PATH = Path(__file__).resolve().parents[1] / "data" / "gateway.db"


def get_conn() -> sqlite3.Connection:
    """获取 SQLite 连接，开启 WAL 模式，返回 Row 对象。"""
    conn = sqlite3.connect(str(DB_PATH))
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA foreign_keys=ON")
    conn.row_factory = sqlite3.Row
    return conn


def init_db() -> None:
    """创建所有表，幂等。"""
    conn = get_conn()
    conn.executescript("""
        CREATE TABLE IF NOT EXISTS gateways (
            id          TEXT PRIMARY KEY,
            name        TEXT NOT NULL,
            description TEXT DEFAULT '',
            created_at  TEXT NOT NULL DEFAULT (datetime('now')),
            updated_at  TEXT NOT NULL DEFAULT (datetime('now'))
        );

        CREATE TABLE IF NOT EXISTS tools (
            name        TEXT NOT NULL,
            gateway_id  TEXT NOT NULL REFERENCES gateways(id),
            description TEXT NOT NULL,
            http_method TEXT NOT NULL,
            http_url    TEXT NOT NULL,
            param_schema TEXT NOT NULL,
            sort_order  INTEGER DEFAULT 0,
            PRIMARY KEY (gateway_id, name)
        );

        CREATE TABLE IF NOT EXISTS sessions (
            session_id  TEXT PRIMARY KEY,
            gateway_id  TEXT NOT NULL,
            created_at  REAL NOT NULL,
            last_active REAL NOT NULL
        );

        CREATE INDEX IF NOT EXISTS idx_sessions_last_active ON sessions(last_active);
    """)
    conn.commit()
    conn.close()
