# Hao AI MCP Gateway

AI MCP Gateway（前后端一体）项目，目标是把 HTTP API（Swagger/OpenAPI）快速转换为可被大模型调用的 MCP 工具能力，并提供管理后台完成网关、协议、工具、鉴权的配置与运维。

## 项目目标

- 将现有 HTTP 接口以低成本接入 MCP。
- 通过管理后台完成网关配置、协议导入、工具挂载、鉴权限流配置。
- 支持 SSE 消息通道，提供 MCP 消息处理链路（鉴权、会话、工具调用）。
- 基于 DDD + 六边形架构，保证领域层稳定和可扩展。

## 核心能力

- 协议导入：导入 Swagger/OpenAPI JSON，解析为协议与字段映射。
- 工具挂载：将协议绑定到网关工具，形成可调用 MCP 工具函数。
- 鉴权与限流：按网关发放 API Key，支持调用限流与状态控制。
- 网关治理：网关基础信息配置、工具列表、协议列表、鉴权列表管理。
- 消息编排：MCP 消息按责任链处理（前置校验 -> 会话校验 -> 业务处理）。

## 工程结构

```text
Hao-ai-mcp-gateway
├── Hao-ai-mcp-gateway-api              # API 协议、DTO、统一响应对象
├── Hao-ai-mcp-gateway-app              # 启动模块（Spring Boot）、配置、Mapper XML
├── Hao-ai-mcp-gateway-trigger          # HTTP 入口层（Controller）
├── Hao-ai-mcp-gateway-case             # 用例编排层（跨领域流程）
├── Hao-ai-mcp-gateway-domain           # 领域层（网关、协议、鉴权、会话）
├── Hao-ai-mcp-gateway-infrastructure   # 基础设施层（DAO、Repository、Port实现）
└── Hao-ai-mcp-gateway-types            # 通用枚举、异常、常量
```

## 技术栈

- Java 17
- Spring Boot 3.4.3
- MyBatis 3.0.4
- MySQL 8.x
- Maven 多模块
- WebFlux（SSE）
- 前端：Bootstrap + jQuery（静态页面）

## 架构说明（简版）

- Trigger：只做协议接入、参数适配、结果封装。
- Case：流程编排与跨领域协调。
- Domain：业务规则与领域模型。
- Infrastructure：数据库访问、外部资源对接。
- API：对外协议定义（DTO、Response）。

## 关键流程

### 1）从 Swagger 到 MCP 工具

1. 管理端上传 Swagger/OpenAPI JSON。
2. 后端解析 endpoint 与字段映射（request/response mapping）。
3. 存储协议信息与映射关系。
4. 网关工具绑定 protocolId，形成 MCP 可调用工具。
5. 大模型通过网关进行工具调用。

### 2）推荐配置顺序

1. 新建网关（gatewayId）
2. 导入协议（OpenAPI）
3. 配置网关工具（关联协议）
4. 配置鉴权（API Key + 限流）
5. 使用 SSE + MCP 消息调用验证

## 快速启动

### 1）准备环境

- JDK 17
- Maven 3.8+
- MySQL 8.x（本地或容器）

### 2）初始化数据库

- 执行 SQL 脚本：
  - `docs/dev-ops/mysql/sql/xfg-frame-archetype.sql`
- 默认库名示例：`ai_mcp_gateway_v2`

### 3）配置后端

编辑：

- `Hao-ai-mcp-gateway-app/src/main/resources/application-dev.yml`

重点检查：

- `server.port`（当前常用 8092）
- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`

### 4）启动后端

在项目根目录执行：

```bash
mvn -pl Hao-ai-mcp-gateway-app -am spring-boot:run
```

或先编译：

```bash
mvn -pl Hao-ai-mcp-gateway-app -am clean compile -DskipTests=true
```

### 5）启动前端管理端

前端目录：

- `docs/dev-ops/ngnix/html`

启动静态服务：

```bash
cd docs/dev-ops/ngnix/html
python3 -m http.server 9001
```

访问：

- 登录页：`http://localhost:9001/`
- 管理页：`http://localhost:9001/admin.html`

默认测试账号（前端 mock）：

- 用户名：`admin`
- 密码：`password123`

## 接口分组

当前有两组管理接口：

### A. 新版管理接口（推荐）

- 前缀：`/api/admin`
- 典型接口：
  - `POST /api/admin/gateway/config`
  - `POST /api/admin/gateway/tool`
  - `POST /api/admin/gateway/protocol`
  - `POST /api/admin/gateway/auth`
  - `GET  /api/admin/gateway/list`
  - `GET  /api/admin/meta`

### B. 前端兼容接口（供当前 admin 前端使用）

- 前缀：`/api-gateway/admin`
- 包含网关、工具、协议、鉴权的 list/page/save/delete/import/analysis 能力。

## MCP 调用入口

SSE 入口样例：

- `GET /{gatewayId}/mcp/sse`
- `POST /{gatewayId}/mcp/sse`

说明：

- `gatewayId` 对应网关配置。
- 建议携带有效 `api_key` 完成鉴权。

## 常见问题排查

### 1）数据库连不上

- 检查 JDBC URL 是否正确（不要误写为 `jdbc:mysql:aws://...`）。
- 检查 MySQL 端口、用户名、密码、库名是否一致。
- 修改配置后必须重启后端。

### 2）工具新增报唯一键冲突

- `toolId` 在库中存在唯一约束。
- 当前已支持重复 toolId 时执行更新逻辑（而非直接失败）。

### 3）前端接口 404

- 确认后端已重启并加载最新 Controller。
- 确认前端 `js/config.js` 的 API 地址和端口匹配当前后端。

### 4）前端跨域报错

- 项目 Controller 已开启 `@CrossOrigin`。
- 如有网关/代理层，请检查转发与 CORS 头配置。

## 开发建议

- 领域规则优先放在 Domain，不在 Controller 写业务逻辑。
- Trigger 只做协议适配，Case 做跨域编排。
- 新增能力先补 API DTO，再扩展 case/domain/infra。
- 前端优先复用现有列表与弹窗交互，避免重复页面逻辑。

## Roadmap（可选）

- 增加真实账号体系（替换前端 mock 登录）。
- 增加协议版本管理与回滚。
- 增加工具灰度发布与调用审计。
- 增加网关监控面板（QPS、失败率、限流命中率）。

## 致谢

- 项目工程思想参考 DDD 与六边形架构实践。
- 原始工程模版来源于小傅哥的工程体系，当前仓库已完成 MCP 网关方向的业务化演进。
