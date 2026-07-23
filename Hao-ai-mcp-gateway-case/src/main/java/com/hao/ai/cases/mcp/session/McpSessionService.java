package com.hao.ai.cases.mcp.session;

import com.alibaba.fastjson.JSON;
import com.hao.ai.cases.mcp.IMcpSessionService;
import com.hao.ai.domain.session.IMcpCoreHandler;
import com.hao.ai.domain.session.ISessionManagementService;
import com.hao.ai.domain.session.model.valobj.McpSchemaVO;
import com.hao.ai.domain.session.model.valobj.SessionVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * SSE transport 用例编排服务。
 * <p>
 * CreateMcpSession：调 domain 层创建会话，返回 sink 的 Flux（SSE 长连接）
 * handleMessage：复用 CoreHandler 处理消息，结果通过 sink 推回长连接（而非 HTTP 响应体）
 */
@Slf4j
@Service
public class McpSessionService implements IMcpSessionService {

    @Resource
    private ISessionManagementService sessionManagementService;

    @Resource
    private IMcpCoreHandler coreHandler;

    @Override
    public Flux<ServerSentEvent<String>> CreateMcpSession(String gatewayId, String apiKey) throws Exception {
        log.info("创建 SSE 会话 gatewayId:{}", gatewayId);

        // 调 domain 层创建会话（含 sink + endpoint 事件推送）
        SessionVO session = sessionManagementService.createSession(gatewayId, apiKey);

        // sink 转 Flux 返回给 Controller，WebFlux 持续推送 SSE 事件
        return session.getSink().asFlux();
    }

    @Override
    public ResponseEntity<Object> handleMessage(String gatewayId, String sessionId, String apiKey, String body) {
        log.info("处理 SSE 消息 gatewayId:{} sessionId:{}", gatewayId, sessionId);

        try {
            // 1. 校验会话
            SessionVO session = sessionManagementService.getSession(sessionId);
            if (session == null) {
                log.warn("SSE 会话不存在或已过期 sessionId:{}", sessionId);
                return ResponseEntity.notFound().build();
            }

            // 2. 解析 JSON-RPC 消息
            McpSchemaVO.JSONRPCMessage message = McpSchemaVO.deserializeJsonRpcMessage(body);

            // 3. 通知类 → 202（v1 不处理通知）
            if (message instanceof McpSchemaVO.JSONRPCNotification) {
                return ResponseEntity.accepted().build();
            }
            if (message instanceof McpSchemaVO.JSONRPCResponse) {
                return ResponseEntity.accepted().build();
            }

            // 4. 请求类 → CoreHandler 处理 → 结果推 sink
            if (message instanceof McpSchemaVO.JSONRPCRequest request) {
                McpSchemaVO.JSONRPCResponse response = coreHandler.handle(gatewayId, apiKey, request);

                // 结果通过 SSE 长连接推回（与 Streamable HTTP 的区别：推 sink 而非返回响应体）
                if (response != null && session.getSink() != null) {
                    String responseJson = JSON.toJSONString(response);
                    session.getSink().tryEmitNext(ServerSentEvent.<String>builder()
                            .event("message")
                            .data(responseJson)
                            .build());
                }

                return ResponseEntity.accepted().build();
            }

            return ResponseEntity.accepted().build();

        } catch (com.hao.ai.types.exception.AppException e) {
            log.warn("SSE 消息处理业务异常 gatewayId:{} {}", gatewayId, e.getMessage());
            return ResponseEntity.accepted().build();
        } catch (Exception e) {
            log.error("SSE 消息处理失败 gatewayId:{} sessionId:{}", gatewayId, sessionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
