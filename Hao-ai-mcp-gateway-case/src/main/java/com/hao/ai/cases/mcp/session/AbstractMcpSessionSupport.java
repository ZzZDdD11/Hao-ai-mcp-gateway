package com.hao.ai.cases.mcp.session;

import cn.bugstack.wrench.design.framework.tree.AbstractMultiThreadStrategyRouter;
import com.hao.ai.cases.mcp.session.factory.DefaultMcpSessionFactory;
import com.hao.ai.domain.session.ISessionManagementService;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import javax.annotation.Resource;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public abstract class AbstractMcpSessionSupport extends AbstractMultiThreadStrategyRouter<String, DefaultMcpSessionFactory.DynamicContext, Flux<ServerSentEvent<String>>> {
    @Resource
    protected ISessionManagementService sessionManagementService;

    @Override
    protected void multiThread(String requestParameter, DefaultMcpSessionFactory.DynamicContext dynamicContext) throws ExecutionException, InterruptedException, TimeoutException {

    }
}
