#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Hao AI MCP Gateway —— 工具自动注入脚本

把 HTTP 服务（Swagger/OpenAPI）自动注入成指定网关下的 MCP 工具。
零后端改动：复用已有的 /api/admin/protocol/import-bind 接口。

用法示例：
  # 1) 从服务地址自动发现 OpenAPI 并全量导入（推荐）
  ./scripts/import_tools.py -g pm-dashboard -u http://pm-dashboard:8080

  # 2) 从本地 OpenAPI JSON 文件导入
  ./scripts/import_tools.py -g pm-dashboard -f ./openapi.json

  # 3) 只预览不落库（dry-run）
  ./scripts/import_tools.py -g pm-dashboard -u http://pm-dashboard:8080 --dry-run

  # 4) 批量：多个服务一次性导入到同一网关
  ./scripts/import_tools.py -g pm-dashboard -u http://svc1:8080 -u http://svc2:8080

环境变量：
  MCP_GATEWAY_BASE_URL   后端地址，默认 http://localhost:8092
                         （Docker 部署对外端口是 8080）
"""
import argparse
import json
import sys
import urllib.error
import urllib.request

# 常见 OpenAPI 暴露端点，按优先级尝试
OPENAPI_ENDPOINTS = [
    "/v3/api-docs",
    "/v2/api-docs",
    "/swagger/v1/swagger.json",
    "/api-docs",
    "/openapi.json",
]


def http_get_json(url, timeout=15):
    req = urllib.request.Request(url, headers={"Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        body = resp.read().decode("utf-8")
        return json.loads(body)


def http_post_json(url, payload, timeout=60):
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        url, data=data, headers={"Content-Type": "application/json"}, method="POST"
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        body = resp.read().decode("utf-8")
        return json.loads(body)


def is_openapi_doc(data):
    return isinstance(data, dict) and ("paths" in data or "openapi" in data or "swagger" in data)


def discover_openapi(base):
    """从服务根地址自动发现 OpenAPI 文档。"""
    base = base.rstrip("/")
    if base.endswith(("openapi.json", "swagger.json")) or any(
        base.endswith(ep) for ep in OPENAPI_ENDPOINTS
    ):
        return http_get_json(base)

    tried = []
    for ep in OPENAPI_ENDPOINTS:
        url = base + ep
        tried.append(url)
        try:
            data = http_get_json(url)
            if is_openapi_doc(data):
                print(f"  [发现 OpenAPI] {url}")
                return data
        except Exception as e:
            print(f"  [跳过] {url} -> {type(e).__name__}")
    raise RuntimeError(f"无法从 {base} 自动发现 OpenAPI，已尝试: {', '.join(tried)}")


def load_openapi_from_file(path):
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    if not is_openapi_doc(data):
        raise RuntimeError(f"{path} 不是有效的 OpenAPI 文档（缺少 paths/openapi/swagger）")
    print(f"  [读取文件] {path}")
    return data


def main():
    p = argparse.ArgumentParser(description="把 HTTP 服务自动注入成 MCP 网关工具")
    p.add_argument("-g", "--gateway", required=True, help="目标网关 ID，如 pm-dashboard")
    p.add_argument("-u", "--url", action="append", default=[], help="服务地址或 OpenAPI 地址，可多次")
    p.add_argument("-f", "--file", action="append", default=[], help="本地 OpenAPI JSON 文件，可多次")
    p.add_argument("-b", "--base-url", default=None, help="后端地址，默认取环境变量或 http://localhost:8092")
    p.add_argument("--dry-run", action="store_true", help="只解析预览，不落库")
    args = p.parse_args()

    import os
    base_url = args.base_url or os.environ.get("MCP_GATEWAY_BASE_URL", "http://localhost:8092")

    if not args.url and not args.file:
        p.error("至少提供一个 -u/--url 或 -f/--file")

    docs = []  # (来源标识, openapi dict)
    for u in args.url:
        print(f"[拉取] {u}")
        docs.append((u, discover_openapi(u)))
    for f in args.file:
        docs.append((f, load_openapi_from_file(f)))

    for source, doc in docs:
        print(f"\n=== 来源: {source} -> 网关: {args.gateway} ===")
        endpoint = "/api/admin/protocol/parse" if args.dry_run else "/api/admin/protocol/import-bind"
        payload = {"gatewayId": args.gateway, "openApiJson": doc}

        try:
            resp = http_post_json(base_url.rstrip("/") + endpoint, payload)
        except urllib.error.HTTPError as e:
            print(f"  请求失败 HTTP {e.code}: {e.read().decode('utf-8', 'ignore')}")
            continue
        except Exception as e:
            print(f"  请求异常: {e}")
            continue

        if resp.get("code") != "0000":
            print(f"  失败: {resp.get('info') or resp}")
            continue

        if args.dry_run:
            items = resp.get("data") or []
            print(f"  解析到 {len(items)} 个接口（未落库）：")
            for it in items:
                print(f"    - {it.get('toolName')} [{it.get('httpMethod')}] {it.get('httpUrl')} · {it.get('toolDescription') or ''}")
        else:
            data = resp.get("data") or {}
            created = data.get("created") or []
            skipped = data.get("skipped") or []
            print(f"  成功创建 {len(created)} 个工具" + (f"，跳过 {len(skipped)} 个重复" if skipped else ""))
            for n in created:
                print(f"    ✓ {n}")
            for n in skipped:
                print(f"    - 跳过（已存在）: {n}")

    print("\n完成。")


if __name__ == "__main__":
    main()
