package com.hao.ai.cases.mcp.message;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.hao.ai.cases.mcp.IMcpMessageService;
import com.hao.ai.cases.mcp.message.factory.DefaultMcpMessageFactory;
import com.hao.ai.domain.session.model.entity.HandleMessageCommandEntity;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class McpMessageService implements IMcpMessageService {

    @Resource
    private DefaultMcpMessageFactory defaultMcpMessageFactory;

    @Override
    public ResponseEntity<Void> handleMessage(HandleMessageCommandEntity commandEntity) throws Exception {
        StrategyHandler<HandleMessageCommandEntity, DefaultMcpMessageFactory.DynamicContext, ResponseEntity<Void>> strategyHandler
                = defaultMcpMessageFactory.strategyHandler();

        return strategyHandler.apply(commandEntity, new DefaultMcpMessageFactory.DynamicContext());
    }
}
