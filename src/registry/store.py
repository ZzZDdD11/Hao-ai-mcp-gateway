"""
注册信息的存储和查询
"""

import sqlite3
from pathlib import Path

DB_PATH = Path(__file__).resolve().parents[1] / "data" / "gateway.db"


def get_conn() -> sqlite3.Connection:
    """
    链接数据库
    """
    conn = sqlite3.connect(str(DB_PATH))
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA foreign_keys=ON")
    conn.row_factory = sqlite3.Row
    return conn


def gateway_exists(gateway_id: str) -> bool:
    conn = get_conn()
    row = conn.execute("SELECT 1 FROM gateways WHERE id = ?", (gateway_id)).fetchone()
    conn.close()
    return row is not None
