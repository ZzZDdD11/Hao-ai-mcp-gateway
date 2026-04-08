package com.hao.ai.domain.session.adapter.repository;

import com.hao.ai.domain.session.model.valobj.gateway.McpGatewayConfigVO;
import com.hao.ai.domain.session.model.valobj.gateway.McpToolConfigVO;
import com.hao.ai.domain.session.model.valobj.gateway.McpToolProtocolConfigVO;

import java.util.List;

public interface ISessionRepository {

    McpGatewayConfigVO queryMcpGatewayConfigByGatewayId(String gatewayId);

    List<McpToolConfigVO> queryMcpGatewayToolConfigListByGatewayId(String gatewayId);

    McpToolProtocolConfigVO queryMcpGatewayProtocolConfig(String gatewayId, String toolName);
}
