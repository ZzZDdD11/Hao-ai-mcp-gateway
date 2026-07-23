package com.hao.ai.cases.mcp;

import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

public interface IMcpSessionService {
    Flux<ServerSentEvent<String>> CreateMcpSession(String gatewayId, String apikey) throws Exception;

    /**
     * 处理 SSE transport 的 POST 消息：解析 → 校验会话 → CoreHandler 处理 → 结果推 sink
     *
     * @return 202 Accepted（结果通过 SSE 长连接推回，不在 HTTP 响应体里）
     */
    ResponseEntity<Object> handleMessage(String gatewayId, String sessionId, String apiKey, String body);
}
