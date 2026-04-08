package com.hao.ai.domain.session.service.message;

import com.hao.ai.domain.session.model.valobj.McpSchemaVO;

public interface IRequestHandler {

    McpSchemaVO.JSONRPCResponse handle(String gatewayId, McpSchemaVO.JSONRPCRequest message);
}
