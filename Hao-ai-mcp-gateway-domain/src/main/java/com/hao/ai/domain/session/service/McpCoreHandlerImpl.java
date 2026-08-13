package com.hao.ai.domain.session.service;

import com.hao.ai.domain.auth.IAuthLicenseService;
import com.hao.ai.domain.auth.IAuthRateLimitService;
import com.hao.ai.domain.auth.model.entity.LicenseCommandEntity;
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

    /** 鉴权失败错误码：供 transport 层识别并返回 HTTP 401 */
    public static final String AUTH_FAILED_CODE = "AUTH_FAILED";

    @Resource
    private IAuthRateLimitService authRateLimitService;

    @Resource
    private IAuthLicenseService authLicenseService;

    @Resource
    private ISessionMessageService sessionMessageService;

    @Override
    public McpSchemaVO.JSONRPCResponse handle(String gatewayId, String apiKey,
                                              McpSchemaVO.JSONRPCMessage message) {
        // 0. 鉴权：校验 api_key（网关未开强校验时 checkLicense 内部直接放行，向后兼容）
        if (!authLicenseService.checkLicense(new LicenseCommandEntity(gatewayId, apiKey))) {
            log.warn("核心处理鉴权失败 gatewayId:{} apiKey:{}", gatewayId, apiKey);
            throw new AppException(AUTH_FAILED_CODE, "api_key 鉴权失败");
        }

        // 1. 限流：仅 tools/call 命中限流（与 RootNode 逻辑一致）
        if (message instanceof McpSchemaVO.JSONRPCRequest request) {
            String method = request.method();
            SessionMessageHandlerMethodEnum methodEnum = SessionMessageHandlerMethodEnum.getByMethod(method);
            if (SessionMessageHandlerMethodEnum.TOOLS_CALL.equals(methodEnum)) {
                boolean hit = authRateLimitService.rateLimit(
                        new RateLimitCommandEntity(gatewayId, apiKey));
                if (hit) {
                    log.warn("核心处理命中限流 gatewayId:{} apiKey:{}", gatewayId, apiKey);
                    throw new AppException("RATE_LIMITED", "fail to auth apikey rateLimiter");
                }
            }
        }

        // 2. 消息分发：按 method 路由到对应 IRequestHandler（initialize/tools-list/tools-call）
        return sessionMessageService.processHandlerMessage(gatewayId, message);
    }
}
