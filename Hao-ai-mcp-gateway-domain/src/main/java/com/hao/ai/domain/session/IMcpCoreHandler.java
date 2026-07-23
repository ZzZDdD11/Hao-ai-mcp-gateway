package com.hao.ai.domain.session;

import com.hao.ai.domain.session.model.valobj.McpSchemaVO;

/**
 * Transport 无关的核心消息处理服务。
 * <p>
 * 将"限流 + 消息分发"从 SSE transport 的责任链中提炼出来，
 * 供 SSE transport 和 Streamable HTTP transport 共享复用。
 * <p>
 * 会话校验不放在此处 —— 两种 transport 的会话标识方式不同
 * （SSE 用 URL sessionId 参数，Streamable HTTP 用 Mcp-Session-Id header），
 * 由各自 transport 层独立校验后再调用本接口。
 */
public interface IMcpCoreHandler {

    /**
     * 处理单条 JSON-RPC 消息：限流校验（仅 tools/call）→ 消息分发到 IRequestHandler。
     *
     * @param gatewayId 网关 ID
     * @param apiKey    调用方 API Key（用于限流分桶）
     * @param message   JSON-RPC 消息（请求/通知/响应）
     * @return JSON-RPC 响应；通知类消息返回 null
     */
    McpSchemaVO.JSONRPCResponse handle(String gatewayId, String apiKey,
                                       McpSchemaVO.JSONRPCMessage message);
}
