package com.hao.ai.cases.mcp.streamable;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hao.ai.cases.mcp.IMcpStreamableHttpService;
import com.hao.ai.domain.session.IMcpCoreHandler;
import com.hao.ai.domain.session.ISessionManagementService;
import com.hao.ai.domain.session.model.valobj.McpSchemaVO;
import com.hao.ai.domain.session.model.valobj.SessionVO;
import com.hao.ai.domain.session.service.McpCoreHandlerImpl;
import com.hao.ai.types.exception.AppException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * Streamable HTTP transport 用例编排实现。
 * <p>
 * 流程：解析消息 → 区分 initialize/普通请求/通知 → 会话校验 → CoreHandler 处理 → 封装响应
 */
@Slf4j
@Service
public class McpStreamableHttpService implements IMcpStreamableHttpService {

    @Resource
    private IMcpCoreHandler coreHandler;

    @Resource
    private ISessionManagementService sessionManagementService;

    @Override
    public ResponseEntity<Object> handleMessage(String gatewayId, String sessionId,
                                                String apiKey, String body) {
        Object requestId = extractId(body);

        try {
            // 1. 解析 JSON-RPC 消息（McpSchemaVO 内置类型判断）
            McpSchemaVO.JSONRPCMessage message = McpSchemaVO.deserializeJsonRpcMessage(body);

            // 2. 通知类 → 202 Accepted（v1 不处理通知，直接确认）
            if (message instanceof McpSchemaVO.JSONRPCNotification) {
                log.info("Streamable HTTP 收到通知，返回 202 gatewayId:{}", gatewayId);
                return ResponseEntity.accepted().build();
            }

            // 3. 响应类 → 202 Accepted
            if (message instanceof McpSchemaVO.JSONRPCResponse) {
                return ResponseEntity.accepted().build();
            }

            // 4. 请求类处理
            if (message instanceof McpSchemaVO.JSONRPCRequest request) {
                return handleRequest(gatewayId, sessionId, apiKey, request);
            }

            // 未知类型
            return ResponseEntity.badRequest().body(
                    buildErrorResponse(requestId, -32600, "Invalid Request"));

        } catch (AppException e) {
            // 鉴权失败 → HTTP 401（避免被扫描器误判为"无鉴权"）
            if (McpCoreHandlerImpl.AUTH_FAILED_CODE.equals(e.getCode())) {
                log.warn("Streamable HTTP 鉴权失败 gatewayId:{} {}", gatewayId, e.getInfo());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED.value()).body(
                        buildErrorResponse(requestId, -32001, e.getInfo()));
            }
            // 限流等业务异常 → JSON-RPC error（HTTP 200，错误在 body 里）
            log.warn("Streamable HTTP 业务异常 gatewayId:{} {}", gatewayId, e.getMessage());
            return ResponseEntity.ok().body(
                    buildErrorResponse(requestId, -32000, e.getMessage()));

        } catch (Exception e) {
            log.error("Streamable HTTP 消息处理失败 gatewayId:{}", gatewayId, e);
            return ResponseEntity.internalServerError().body(
                    buildErrorResponse(requestId, -32603, "Internal error"));
        }
    }

    @Override
    public ResponseEntity<Object> terminateSession(String gatewayId, String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        SessionVO session = sessionManagementService.getSession(sessionId);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }

        sessionManagementService.removeSession(sessionId);
        log.info("Streamable HTTP 会话终止 gatewayId:{} sessionId:{}", gatewayId, sessionId);
        return ResponseEntity.ok().build();
    }

    /**
     * 处理 JSON-RPC 请求（initialize 或普通请求）
     */
    private ResponseEntity<Object> handleRequest(String gatewayId, String sessionId,
                                                 String apiKey,
                                                 McpSchemaVO.JSONRPCRequest request) {
        String method = request.method();

        // initialize → 创建 Streamable HTTP 会话
        if ("initialize".equals(method)) {
            SessionVO session = sessionManagementService.createStreamableSession(gatewayId);
            String newSessionId = session.getSessionId();

            McpSchemaVO.JSONRPCResponse response = coreHandler.handle(gatewayId, apiKey, request);

            // 响应头带 Mcp-Session-Id，客户端后续请求需携带
            return ResponseEntity.ok()
                    .header("Mcp-Session-Id", newSessionId)
                    .body(response);
        }

        // 其他请求 → 校验会话
        if (sessionId == null || sessionId.isEmpty()) {
            // 缺少 Mcp-Session-Id header → 400
            return ResponseEntity.badRequest().body(
                    buildErrorResponse(request.id(), -32600, "Missing Mcp-Session-Id header"));
        }

        SessionVO session = sessionManagementService.getSession(sessionId);
        if (session == null) {
            // 会话不存在/过期 → 404（客户端需重新 initialize）
            return ResponseEntity.notFound().build();
        }

        // 更新最后访问时间（复用现有心跳机制）
        session.updateLastAccessed();

        // 核心处理：限流 + 消息分发
        McpSchemaVO.JSONRPCResponse response = coreHandler.handle(gatewayId, apiKey, request);

        return ResponseEntity.ok().body(response);
    }

    /**
     * 从 body 提取 JSON-RPC id（异常时构建 error 响应用）
     */
    private Object extractId(String body) {
        try {
            JSONObject json = JSON.parseObject(body);
            return json.get("id");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 构建 JSON-RPC error 响应
     *
     * @param id      原始请求 id
     * @param code    JSON-RPC 错误码（-32000 服务端错误 / -32600 非法请求 / -32603 内部错误）
     * @param message 错误描述
     */
    private McpSchemaVO.JSONRPCResponse buildErrorResponse(Object id, int code, String message) {
        return new McpSchemaVO.JSONRPCResponse(
                McpSchemaVO.JSONRPC_VERSION,
                id,
                null,
                new McpSchemaVO.JSONRPCResponse.JSONRPCError(code, message, null)
        );
    }
}
