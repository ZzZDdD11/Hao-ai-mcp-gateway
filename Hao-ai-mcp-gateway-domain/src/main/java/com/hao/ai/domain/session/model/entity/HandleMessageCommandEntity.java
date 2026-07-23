package com.hao.ai.domain.session.model.entity;

import com.hao.ai.domain.session.model.valobj.McpSchemaVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息处理命令实体（SSE transport 消息责任链入参）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class HandleMessageCommandEntity {

    /**
     * 网关 ID
     */
    private String gatewayId;

    /**
     * API Key（限流分桶用）
     */
    private String apiKey;

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * JSON-RPC 消息
     */
    private McpSchemaVO.JSONRPCMessage jsonrpcMessage;
}
