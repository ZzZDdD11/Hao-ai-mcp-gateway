# Streamable HTTP Transport 实现方案

> 状态：设计中 | 日期：2026-07-23
> 规范来源：MCP 2025-03-26（https://modelcontextprotocol.io/specification/2025-03-26/basic/transports）

---

## 1. 背景与目标

### 现状
网关目前只实现了 SSE transport（MCP 2024-11-05 旧规范，双端点 `GET /mcp/sse` + `POST /mcp/sse`），该规范已被官方标记 deprecated。MCP 2025-03-26 引入 Streamable HTTP（单端点）作为新标准。

### 目标
新增 Streamable HTTP transport，与现有 SSE transport **共存**（向后兼容），让网关同时支持新旧两种传输模式。

### 不做
- 不删除现有 SSE transport（向后兼容旧客户端）
- 不改 domain 层核心业务逻辑（`SessionMessageService`、`IRequestHandler`、`AuthRateLimitService` 可直接复用）

---

## 2. 协议对比（设计依据）

| 维度 | SSE Transport（现有） | Streamable HTTP（新增） |
|------|---------------------|----------------------|
| 端点 | 2 个（GET /mcp/sse + POST /mcp/sse） | 1 个（POST/GET/DELETE /mcp 共用） |
| 会话标识 | URL 参数 `sessionId` | HTTP Header `Mcp-Session-Id` |
| 连接模型 | 必须先 GET 建长连接，再 POST 发消息 | 直接 POST，无需预先建连 |
| 响应返回 | 结果从 GET 长连接的 SSE 流推回（请求与响应走不同通道） | 结果直接在 POST 的 HTTP 响应体返回（JSON 或 SSE 流） |
| 会话终止 | 无显式机制（靠超时清理） | `DELETE /mcp` + `Mcp-Session-Id` header |
| 服务端推送 | 原生（SSE 长连接一直在） | 可选（客户端 `GET /mcp` 建立可选 SSE 流） |
| 无状态支持 | 不支持（必须维持长连接） | 支持（可不分配 sessionId，每请求独立） |

---

## 3. 端点设计

```
现有 SSE transport（保留，向后兼容）:
  GET  /{gatewayId}/mcp/sse?api_key=xxx      建立 SSE 长连接
  POST /{gatewayId}/mcp/sse                   发送 JSON-RPC

新增 Streamable HTTP:
  POST   /{gatewayId}/mcp                     发送 JSON-RPC（核心端点）
  GET    /{gatewayId}/mcp                     可选，打开 SSE 流（服务端主动推送）
  DELETE /{gatewayId}/mcp                     终止会话
```

两种 transport 通过 URL 路径区分（`/mcp` vs `/mcp/sse`），互不干扰。

---

## 4. 核心设计：传输层抽象

现有责任链（RootNode→SessionNode→MessageHandlerNode）的 `MessageHandlerNode` 把结果推到 `sessionVO.getSink()`——这是 SSE transport 专属的"结果推长连接"模式。Streamable HTTP 需要结果直接在 HTTP 响应体返回，不能走 sink。

### 方案：抽出传输无关的核心处理服务

把"限流校验 + 会话校验 + 消息处理"从责任链节点里提炼为可复用的核心服务，两种 transport 各自只负责"协议接入 + 结果封装"：

```java
// domain 层：传输无关的核心处理服务（新建）
public interface IMcpCoreHandler {
    /**
     * 处理单条 JSON-RPC 消息：限流 → 会话校验 → 分发到 IRequestHandler
     * @return JSONRPCResponse（通知类返回 null）
     */
    McpSchemaVO.JSONRPCResponse handle(String gatewayId, String apiKey, 
                                        String sessionId, 
                                        McpSchemaVO.JSONRPCMessage message);
}
```

实现复用现有逻辑：
- 限流：`AuthRateLimitService.rateLimit(gatewayId, apiKey)`（仅 tools/call）
- 会话校验：`SessionManagementService.getSession(sessionId)`
- 消息分发：`SessionMessageService.processHandlerMessage(gatewayId, message)`

```
                    ┌─────────────────────────┐
                    │  IMcpCoreHandler（复用）  │
                    │  限流 → 会话校验 → 分发    │
                    └────────┬────────────────┘
                             │ JSONRPCResponse
              ┌──────────────┴──────────────┐
              ▼                             ▼
   SSE Transport                    Streamable HTTP
   结果推 sink.tryEmitNext           结果包成 HTTP 响应体
   （现有责任链）                     （新增 Controller）
```

> 面试亮点：传输层与业务逻辑分离，新增 transport 不改核心处理，符合开闭原则。

---

## 5. 会话管理适配

### 问题
现有 `SessionVO` 绑定了 `Sinks.Many<ServerSentEvent>` sink（SSE 长连接推送通道）。Streamable HTTP 的无状态模式不需要 sink。

### 方案：SessionVO 的 sink 改为可选

```java
public class SessionVO {
    private String sessionId;
    private Sinks.Many<ServerSentEvent<String>> sink;  // nullable：SSE transport 用，Streamable HTTP 无状态模式可空
    private String transportType;  // 新增："sse" | "streamable_http"
    // ... createTime/lastAccessedTime/active 不变
}
```

- SSE transport 创建会话：带 sink
- Streamable HTTP initialize 创建会话：sink 可空（有状态模式）或不创建会话（无状态模式）
- `SessionManagementService.createSession` 增加重载，支持不传 sink

### 会话生命周期（Streamable HTTP）

```
initialize（POST，无 Mcp-Session-Id header）
  → 创建会话，生成 sessionId
  → 响应头 Mcp-Session-Id: {sessionId}
  → 响应体 InitializeResult JSON

后续请求（POST，带 Mcp-Session-Id header）
  → 从 header 取 sessionId
  → SessionManagementService.getSession(sessionId) 校验
  → 不存在/过期 → HTTP 404（客户端需重新 initialize）

终止会话（DELETE，带 Mcp-Session-Id header）
  → SessionManagementService.removeSession(sessionId)
  → HTTP 200
```

---

## 6. POST 请求处理流程（核心）

```
POST /{gatewayId}/mcp
  Headers: Mcp-Session-Id?(首次无), Accept: application/json, text/event-stream
  Body: JSON-RPC message（单个或批量）
    │
    ├─ 1. 鉴权：校验 api_key（从 header 或 query）
    │
    ├─ 2. 解析 Body 为 McpSchemaVO.JSONRPCMessage
    │
    ├─ 3. 判断消息类型：
    │    ├─ 通知（notification）→ 处理后返回 202 Accepted（无 body）
    │    ├─ 请求（request）→ 继续
    │    └─ 响应（response）→ 返回 202 Accepted
    │
    ├─ 4. 会话处理：
    │    ├─ method == initialize → 创建会话，生成 sessionId（待会响应头返回）
    │    └─ 其他 → 从 Mcp-Session-Id header 取 sessionId，校验会话
    │              缺失 header → 400 Bad Request
    │              会话不存在 → 404 Not Found
    │
    ├─ 5. 核心处理：IMcpCoreHandler.handle(gatewayId, apiKey, sessionId, message)
    │    → 限流（tools/call）→ processHandlerMessage → JSONRPCResponse
    │
    ├─ 6. 封装响应：
    │    ├─ initialize → JSON 响应 + 响应头 Mcp-Session-Id
    │    ├─ tools/list → JSON 响应（Content-Type: application/json）
    │    ├─ tools/call → JSON 响应（或 SSE 流，若结果需流式）
    │    └─ 命中限流 → JSON-RPC error 响应
    │
    └─ 返回
```

### 响应格式选择规则

| 场景 | Content-Type | 说明 |
|------|-------------|------|
| initialize / tools/list | `application/json` | 单个 JSON-RPC 响应 |
| tools/call（同步结果） | `application/json` | 单个 JSON-RPC 响应 |
| tools/call（流式结果） | `text/event-stream` | SSE 流，最终含一个响应 |
| 通知/响应类输入 | 无 body，HTTP 202 | Accepted |
| 限流/错误 | `application/json` | JSON-RPC error |

> v1 先实现 JSON 响应模式（简单、够用）；SSE 流式响应作为后续增强。

---

## 7. GET 请求（可选，服务端推送）

```
GET /{gatewayId}/mcp
  Headers: Mcp-Session-Id, Accept: text/event-stream
    │
    ├─ 校验会话
    ├─ 返回 SSE 流（Flux<ServerSentEvent>）
    └─ 服务端可在此流上推送与当前请求无关的通知/请求
```

> v1 可返回 405 Method Not Allowed（不支持服务端推送），v2 再实现。规范允许。

---

## 8. DELETE 请求（会话终止）

```
DELETE /{gatewayId}/mcp
  Headers: Mcp-Session-Id
    │
    ├─ SessionManagementService.removeSession(sessionId)
    └─ HTTP 200（或 405 若拒绝终止）
```

---

## 9. 代码改动清单

### 新增文件

| 层 | 文件 | 职责 |
|----|------|------|
| trigger | `McpStreamableHttpController.java` | POST/GET/DELETE `/{gatewayId}/mcp` 端点，协议接入 + 响应封装 |
| case | `McpStreamableHttpService.java`（implements `IMcpStreamableHttpService`） | Streamable HTTP 专用的用例编排：鉴权→会话→调 coreHandler→封装响应 |
| domain | `IMcpCoreHandler.java` + `McpCoreHandlerImpl.java` | **传输无关的核心处理服务**：限流+会话校验+processHandlerMessage（从责任链提炼） |
| api | `IMcpStreamableHttpService.java` | 接口定义 |

### 修改文件

| 文件 | 改动 |
|------|------|
| `SessionVO.java` | sink 改 nullable，新增 `transportType` 字段 |
| `SessionManagementService.java` | `createSession` 增加重载（支持无 sink）；新增 `createSessionReturnId` 返回 sessionId |
| `app/application.yml` | 无需改（端点由 Controller 注解声明） |

### 不改文件

| 文件 | 原因 |
|------|------|
| `McpGatewayController.java`（SSE） | 保留不动，向后兼容 |
| `SessionMessageService.java` | `processHandlerMessage` 已 transport 无关，直接复用 |
| `InitializeHandler` / `ToolsListHandler` | IRequestHandler 逻辑不变，直接复用 |
| `AuthRateLimitService` | 限流逻辑不变，直接复用 |
| 现有责任链节点 | 保留给 SSE transport 用 |

---

## 10. 关键代码结构示意

### McpStreamableHttpController（trigger 层）

```java
@RestController
@Slf4j
@CrossOrigin
public class McpStreamableHttpController {

    @Resource
    private IMcpStreamableHttpService mcpStreamableHttpService;

    @PostMapping(value = "{gatewayId}/mcp", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Object>> handlePost(
            @PathVariable String gatewayId,
            @RequestHeader(value = "Mcp-Session-Id", required = false) String sessionId,
            @RequestParam(required = false) String api_key,
            @RequestBody String body) {
        return mcpStreamableHttpService.handleMessage(gatewayId, sessionId, api_key, body);
    }

    @DeleteMapping("{gatewayId}/mcp")
    public Mono<ResponseEntity<Object>> handleDelete(
            @PathVariable String gatewayId,
            @RequestHeader("Mcp-Session-Id") String sessionId) {
        return mcpStreamableHttpService.terminateSession(gatewayId, sessionId);
    }

    @GetMapping(value = "{gatewayId}/mcp", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> handleGet(...) {
        // v1: 返回 405 或可选 SSE 流
    }
}
```

### McpCoreHandlerImpl（domain 层，传输无关核心）

```java
@Service
public class McpCoreHandlerImpl implements IMcpCoreHandler {

    @Resource private IAuthRateLimitService authRateLimitService;
    @Resource private ISessionManagementService sessionManagementService;
    @Resource private ISessionMessageService sessionMessageService;

    @Override
    public McpSchemaVO.JSONRPCResponse handle(String gatewayId, String apiKey,
                                               String sessionId,
                                               McpSchemaVO.JSONRPCMessage message) {
        // 1. 限流（仅 tools/call）
        if (message instanceof JSONRPCRequest req) {
            if ("tools/call".equals(req.method())) {
                boolean hit = authRateLimitService.rateLimit(new RateLimitCommandEntity(gatewayId, apiKey));
                if (hit) throw new AppException("fail to auth apikey rateLimiter");
            }
        }
        // 2. 消息分发（processHandlerMessage 内部按 method 路由到 IRequestHandler）
        return sessionMessageService.processHandlerMessage(gatewayId, message);
    }
}
```

> 这段逻辑与现有责任链 RootNode(限流)→SessionNode(会话)→MessageHandlerNode(分发) 等价，只是去掉了 sink 推送，结果直接返回。

---

## 11. 与现有 SSE transport 的关系

```
客户端请求
  │
  ├─ /mcp/sse（旧路径）→ SSE transport（现有责任链，结果推 sink）
  │
  └─ /mcp（新路径）→ Streamable HTTP（新 Controller + CoreHandler，结果返回响应体）
```

- 两种 transport 共享：`SessionManagementService`（会话存储）、`SessionMessageService`（消息分发）、`AuthRateLimitService`（限流）、`IRequestHandler` 实现（initialize/tools-list/tools-call）
- 各自独立：Controller（协议接入）、结果封装方式（sink vs HTTP 响应）

> 规范的向后兼容策略：客户端先尝试 POST `/mcp`（Streamable HTTP），失败再退回 GET `/mcp/sse`（旧 SSE）。网关两种都支持，客户端自动协商。

---

## 12. 实现优先级

| 阶段 | 内容 | 价值 |
|------|------|------|
| P0 | POST `/mcp` + initialize/tools-list/tools-call（JSON 响应） | 打通核心链路，Knot 可用 |
| P1 | DELETE `/mcp` 会话终止 + 会话校验 404 | 会话生命周期完整 |
| P2 | GET `/mcp` 服务端推送 SSE 流 | 可选能力 |
| P3 | POST 响应支持 SSE 流模式（流式 tools/call） | 流式结果 |

---

## 13. 面试可讲的设计决策

1. **传输层抽象**：把限流+会话+分发提炼为 transport 无关的 `IMcpCoreHandler`，新增 transport 不改核心逻辑——开闭原则
2. **共存而非替换**：保留 SSE transport 向后兼容，新增 Streamable HTTP 走不同 URL 路径，客户端自动协商
3. **SessionVO 适配**：sink 改可选，支持 Streamable HTTP 无状态/有状态两种模式
4. **响应格式按需**：v1 用 JSON 响应（简单够用），SSE 流式响应留作增强——不过度设计
5. **复用 domain 层**：processHandlerMessage / IRequestHandler / 限流 全部复用，新增代码集中在 trigger+case 层——体现 DDD 分层价值
