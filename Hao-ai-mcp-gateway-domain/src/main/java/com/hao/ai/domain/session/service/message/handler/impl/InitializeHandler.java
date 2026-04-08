package com.hao.ai.domain.session.service.message.handler.impl;

import com.hao.ai.domain.session.model.valobj.McpSchemaVO;
import com.hao.ai.domain.session.service.message.IRequestHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service("initializeHandler")
public class InitializeHandler implements IRequestHandler {
    @Override
    public McpSchemaVO.JSONRPCResponse handle(String gatewayId, McpSchemaVO.JSONRPCRequest message) {
        log.info("模拟处理初始化请求");
        return null;
    }
}
