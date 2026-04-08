package com.hao.ai.cases.mcp.session.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.hao.ai.cases.mcp.session.AbstractMcpSessionSupport;
import com.hao.ai.cases.mcp.session.factory.DefaultMcpSessionFactory;
import com.hao.ai.types.enums.ResponseCode;
import com.hao.ai.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import jakarta.annotation.Resource;
@Service("mcpSessionVerifyNode")
@Slf4j
public class VerifyNode extends AbstractMcpSessionSupport {

    @Resource(name = "mcpSessionSessionNode")
    private SessionNode sessionNode;

    @Override
    protected Flux<ServerSentEvent<String>> doApply(String requestParameter, DefaultMcpSessionFactory.DynamicContext dynamicContext) throws Exception {
        log.info("创建会话-VerifyNode:{}", requestParameter);

        if (requestParameter == null || requestParameter.isEmpty()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "gatewayId is required");
        }

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<String, DefaultMcpSessionFactory.DynamicContext, Flux<ServerSentEvent<String>>> get(String requestParameter, DefaultMcpSessionFactory.DynamicContext dynamicContext) throws Exception {
        return sessionNode;
    }
}
