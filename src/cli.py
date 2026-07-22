"""CLI 工具：seed 导入种子数据。"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import yaml

from src.db import init_db, get_conn

SEED_PATH = Path(__file__).resolve().parents[1] / "config" / "seed.yaml"


def cmd_seed(seed_file: str) -> None:
    """从 YAML 种子文件导入 gateway 和 tool 数据到 SQLite。"""
    with open(seed_file) as f:
        data = yaml.safe_load(f)

    init_db()
    conn = get_conn()

    for gw in data["gateways"]:
        conn.execute(
            """INSERT OR REPLACE INTO gateways (id, name, description)
               VALUES (?, ?, ?)""",
            (gw["id"], gw["name"], gw.get("description", "")),
        )
        for i, tool in enumerate(gw["tools"]):
            conn.execute(
                """INSERT OR REPLACE INTO tools
                   (name, gateway_id, description, http_method, http_url, param_schema, sort_order)
                   VALUES (?, ?, ?, ?, ?, ?, ?)""",
                (
                    tool["name"],
                    gw["id"],
                    tool["description"],
                    tool["http_method"],
                    tool["http_url"],
                    json.dumps(tool["param_schema"]),
                    tool.get("sort_order", i * 10),
                ),
            )

    conn.commit()
    conn.close()
    print(f"seed 完成，导入了 {len(data['gateways'])} 个 gateway。")


def main() -> None:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)

    p = sub.add_parser("seed", help="导入种子数据")
    p.add_argument("--file", default=str(SEED_PATH), help="seed YAML 路径")

    args = parser.parse_args()
    if args.command == "seed":
        cmd_seed(args.file)


if __name__ == "__main__":
    main()
