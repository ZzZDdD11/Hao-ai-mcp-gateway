package com.hao.ai.domain.session.service;

import com.hao.ai.domain.auth.IAuthRateLimitService;
import com.hao.ai.domain.auth.model.entity.RateLimitCommandEntity;
import com.hao.ai.domain.session.IMcpCoreHandler;
import com.hao.ai.domain.session.ISessionMessageService;
import com.hao.ai.domain.session.model.valobj.McpSchemaVO;
import com.hao.ai.domain.session.model.valobj.enums.SessionMessageHandlerMethodEnum;
import com.hao.ai.types.exception.AppException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * IMcpCoreHandler 实现：transport 无关的核心处理。
 * <p>
 * 逻辑等价于现有责任链 RootNode(限流) → MessageHandlerNode(分发)，
 * 但去掉了 SessionNode（会话校验交由 transport 层）和 sink 推送（结果直接返回）。
 */
@Slf4j
@Service
public class McpCoreHandlerImpl implements IMcpCoreHandler {

    @Resource
    private IAuthRateLimitService authRateLimitService;

    @Resource
    private ISessionMessageService sessionMessageService;

    @Override
    public McpSchemaVO.JSONRPCResponse handle(String gatewayId, String apiKey,
                                              McpSchemaVO.JSONRPCMessage message) {
        // 1. 限流：仅 tools/call 命中限流（与 RootNode 逻辑一致）
        if (message instanceof McpSchemaVO.JSONRPCRequest request) {
            String method = request.method();
            SessionMessageHandlerMethodEnum methodEnum = SessionMessageHandlerMethodEnum.getByMethod(method);
            if (SessionMessageHandlerMethodEnum.TOOLS_CALL.equals(methodEnum)) {
                boolean hit = authRateLimitService.rateLimit(
                        new RateLimitCommandEntity(gatewayId, apiKey));
                if (hit) {
                    log.warn("核心处理命中限流 gatewayId:{} apiKey:{}", gatewayId, apiKey);
                    throw new AppException("fail to auth apikey rateLimiter");
                }
            }
        }

        // 2. 消息分发：按 method 路由到对应 IRequestHandler（initialize/tools-list/tools-call）
        return sessionMessageService.processHandlerMessage(gatewayId, message);
    }
}
