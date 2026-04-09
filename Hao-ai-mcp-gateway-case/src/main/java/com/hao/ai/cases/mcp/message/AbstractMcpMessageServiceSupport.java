package com.hao.ai.cases.mcp.message;

import cn.bugstack.wrench.design.framework.tree.AbstractMultiThreadStrategyRouter;
import com.hao.ai.cases.mcp.message.factory.DefaultMcpMessageFactory;
import com.hao.ai.domain.session.ISessionManagementService;
import com.hao.ai.domain.session.ISessionMessageService;
import com.hao.ai.domain.session.model.entity.HandleMessageCommandEntity;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public abstract class AbstractMcpMessageServiceSupport extends AbstractMultiThreadStrategyRouter<HandleMessageCommandEntity, DefaultMcpMessageFactory.DynamicContext, ResponseEntity<Void>> {
    @Resource
    protected ISessionMessageService serviceMessageService;

    @Resource
    protected ISessionManagementService sessionManagementService;

    @Override
    protected void multiThread(HandleMessageCommandEntity requestParameter, DefaultMcpMessageFactory.DynamicContext dynamicContext) throws ExecutionException, InterruptedException, TimeoutException {

    }
}
