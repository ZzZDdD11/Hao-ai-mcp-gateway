#!/bin/bash
set -e

echo ""
echo "  ╔══════════════════════════════════════╗"
echo "  ║   Hao AI MCP Gateway · 一键部署     ║"
echo "  ╚══════════════════════════════════════╝"
echo ""

# 检测 docker compose 命令
if docker compose version &>/dev/null; then
  DC="docker compose"
elif command -v docker-compose &>/dev/null; then
  DC="docker-compose"
else
  echo "✗ 未检测到 Docker，请先安装 Docker Desktop:"
  echo "  https://www.docker.com/products/docker-desktop"
  exit 1
fi

# Docker 构建并启动（多阶段构建在容器内完成 Maven 编译，不需要本机装 Maven/JDK）
echo "[1/2] Docker 构建并启动（首次较慢，需下载 Maven 依赖）..."
echo "  → MySQL + 后端 + Nginx 三个容器"
$DC up -d --build
echo "  ✓ 容器已启动"

# 等待后端就绪
echo "[2/2] 等待后端服务就绪..."
for i in $(seq 1 60); do
  if curl -s http://localhost:8080/api/admin/gateway/list &>/dev/null; then
    echo "  ✓ 后端已就绪"
    break
  fi
  if [ $i -eq 60 ]; then
    echo "  ⚠ 后端未在 60s 内就绪，请检查日志：$DC logs app"
  fi
  sleep 1
done

echo ""
echo "  ══════════════════════════════════════"
echo "  部署完成！"
echo ""
echo "  前端管理后台:  http://localhost"
echo "  后端 API:      http://localhost:8080"
echo "  MCP 端点:      http://localhost:8080/{gatewayId}/mcp"
echo "  MySQL:         localhost:3306 (root / 12345678)"
echo ""
echo "  查看日志:  $DC logs -f"
echo "  停止服务:  $DC down"
echo "  ══════════════════════════════════════"
echo ""
