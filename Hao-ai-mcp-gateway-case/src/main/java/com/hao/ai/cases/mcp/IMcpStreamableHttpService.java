package com.hao.ai.cases.mcp;

import org.springframework.http.ResponseEntity;

/**
 * Streamable HTTP transport 用例编排服务。
 * <p>
 * 职责：解析 JSON-RPC 消息 → 会话管理（initialize 创建 / 其他校验）
 *       → 调 IMcpCoreHandler 处理 → 封装 HTTP 响应。
 * <p>
 * 返回同步 ResponseEntity，由 Controller 层包装为 Mono。
 */
public interface IMcpStreamableHttpService {

    /**
     * 处理 POST /{gatewayId}/mcp 请求
     *
     * @param gatewayId 网关 ID
     * @param sessionId Mcp-Session-Id header 值（initialize 时为 null）
     * @param apiKey    API Key（鉴权 + 限流分桶）
     * @param body      JSON-RPC 请求体
     * @return HTTP 响应（JSON body 或 202/400/404）
     */
    ResponseEntity<Object> handleMessage(String gatewayId, String sessionId,
                                         String apiKey, String body);

    /**
     * 处理 DELETE /{gatewayId}/mcp 请求（终止会话）
     *
     * @param gatewayId 网关 ID
     * @param sessionId Mcp-Session-Id header 值
     * @return HTTP 200 或 400
     */
    ResponseEntity<Object> terminateSession(String gatewayId, String sessionId);
}
