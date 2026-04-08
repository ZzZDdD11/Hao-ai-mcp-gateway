package com.hao.ai.cases.mcp.session.factory;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.hao.ai.cases.mcp.session.node.RootNode;
import com.hao.ai.domain.session.model.valobj.SessionVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import javax.annotation.Resource;

@Slf4j
@Service
public class DefaultMcpSessionFactory {

    @Resource(name = "mcpSessionRootNode")
    private RootNode rootNode;

    public StrategyHandler<String, DynamicContext, Flux<ServerSentEvent<String>>> strategyHandler() {
        return rootNode;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DynamicContext {

        private String apiKey;

        private SessionVO sessionVO;
    }
}
