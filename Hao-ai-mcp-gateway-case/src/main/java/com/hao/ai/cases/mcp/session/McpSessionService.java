package com.hao.ai.cases.mcp.session;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.hao.ai.cases.mcp.IMcpSessionService;
import com.hao.ai.cases.mcp.session.factory.DefaultMcpSessionFactory;
import com.hao.ai.cases.mcp.session.node.RootNode;
import jakarta.annotation.Resource;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

public class McpSessionService implements IMcpSessionService {

    @Resource
    DefaultMcpSessionFactory defaultMcpSessionFactory;

    @Override
    public Flux<ServerSentEvent<String>> CreateMcpSession(String gatewayId, String apikey) {
        StrategyHandler<String, DefaultMcpSessionFactory.DynamicContext, Flux<ServerSentEvent<String>>> strategyHandler = defaultMcpSessionFactory.strategyHandler();
        return null;
    }
}
