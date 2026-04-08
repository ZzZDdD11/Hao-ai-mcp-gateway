package com.hao.ai.domain.session;

import com.hao.ai.domain.session.model.valobj.McpSchemaVO;

public interface ISessionMessageService {
    McpSchemaVO.JSONRPCResponse processHandlerMessage(String gatewayId, McpSchemaVO.JSONRPCMessage message);

}
