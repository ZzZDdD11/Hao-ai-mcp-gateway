package com.hao.ai.cases.mcp.session.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.hao.ai.cases.mcp.session.AbstractMcpSessionSupport;
import com.hao.ai.cases.mcp.session.factory.DefaultMcpSessionFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Session 责任链末节点（占位实现）。
 * 实际会话创建在 McpSessionService 中直接调 SessionManagementService，此节点保留以维持责任链编译完整。
 */
@Service("mcpSessionSessionNode")
@Slf4j
public class SessionNode extends AbstractMcpSessionSupport {

    @Override
    protected Flux<ServerSentEvent<String>> doApply(String requestParameter, DefaultMcpSessionFactory.DynamicContext dynamicContext) throws Exception {
        log.info("创建会话-SessionNode:{}", requestParameter);
        return Flux.empty();
    }

    @Override
    public StrategyHandler<String, DefaultMcpSessionFactory.DynamicContext, Flux<ServerSentEvent<String>>> get(String requestParameter, DefaultMcpSessionFactory.DynamicContext dynamicContext) throws Exception {
        return null;
    }
}
