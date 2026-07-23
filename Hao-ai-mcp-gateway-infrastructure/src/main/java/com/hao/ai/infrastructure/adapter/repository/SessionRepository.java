package com.hao.ai.infrastructure.adapter.repository;

import com.hao.ai.domain.session.adapter.repository.ISessionRepository;
import com.hao.ai.domain.session.model.valobj.gateway.McpGatewayConfigVO;
import com.hao.ai.domain.session.model.valobj.gateway.McpToolConfigVO;
import com.hao.ai.domain.session.model.valobj.gateway.McpToolProtocolConfigVO;
import com.hao.ai.infrastructure.dao.GatewayDao;
import com.hao.ai.infrastructure.dao.ProtocolHttpDao;
import com.hao.ai.infrastructure.dao.ProtocolMappingDao;
import com.hao.ai.infrastructure.dao.ToolDao;
import com.hao.ai.infrastructure.dao.po.GatewayPO;
import com.hao.ai.infrastructure.dao.po.ProtocolHttpPO;
import com.hao.ai.infrastructure.dao.po.ProtocolMappingPO;
import com.hao.ai.infrastructure.dao.po.ToolPO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ISessionRepository 实现：组装 5 个 DAO，为 tools/list 和 tools/call 提供数据查询。
 * <p>
 * PO（数据库映射）→ VO（领域值对象）的转换在此层完成，domain 层不感知数据库结构。
 */
@Slf4j
@Repository
public class SessionRepository implements ISessionRepository {

    @Resource
    private GatewayDao gatewayDao;

    @Resource
    private ToolDao toolDao;

    @Resource
    private ProtocolHttpDao protocolHttpDao;

    @Resource
    private ProtocolMappingDao protocolMappingDao;

    @Override
    public McpGatewayConfigVO queryMcpGatewayConfigByGatewayId(String gatewayId) {
        GatewayPO po = gatewayDao.queryByGatewayId(gatewayId);
        if (po == null) return null;

        return McpGatewayConfigVO.builder()
                .gatewayId(po.getGatewayId())
                .gatewayName(po.getGatewayName())
                .gatewayDesc(po.getGatewayDesc())
                .version(po.getVersion())
                .build();
    }

    @Override
    public List<McpToolConfigVO> queryMcpGatewayToolConfigListByGatewayId(String gatewayId) {
        List<ToolPO> tools = toolDao.queryByGatewayId(gatewayId);
        if (tools == null || tools.isEmpty()) return Collections.emptyList();

        List<McpToolConfigVO> result = new ArrayList<>();
        for (ToolPO tool : tools) {
            McpToolProtocolConfigVO protocolConfig = queryProtocolConfigByProtocolId(tool.getProtocolId());
            result.add(McpToolConfigVO.builder()
                    .toolName(tool.getToolName())
                    .toolDescription(tool.getToolDescription())
                    .mcpToolProtocolConfigVO(protocolConfig)
                    .build());
        }
        return result;
    }

    @Override
    public McpToolProtocolConfigVO queryMcpGatewayProtocolConfig(String gatewayId, String toolName) {
        ToolPO tool = toolDao.queryByGatewayIdAndToolName(gatewayId, toolName);
        if (tool == null) return null;

        return queryProtocolConfigByProtocolId(tool.getProtocolId());
    }

    /**
     * 按 protocolId 查 HTTP 配置 + 参数映射，组装为 McpToolProtocolConfigVO
     */
    private McpToolProtocolConfigVO queryProtocolConfigByProtocolId(Long protocolId) {
        ProtocolHttpPO httpPo = protocolHttpDao.queryByProtocolId(protocolId);
        if (httpPo == null) return null;

        List<ProtocolMappingPO> mappingPOs = protocolMappingDao.queryByProtocolId(protocolId);
        List<McpToolProtocolConfigVO.ProtocolMapping> mappings = mappingPOs.stream()
                .map(this::convertMapping)
                .collect(Collectors.toList());

        return McpToolProtocolConfigVO.builder()
                .httpUrl(httpPo.getHttpUrl())
                .httpMethod(httpPo.getHttpMethod())
                .httpHeaders(httpPo.getHttpHeaders())
                .timeout(httpPo.getTimeout())
                .requestProtocolMappings(mappings)
                .build();
    }

    private McpToolProtocolConfigVO.ProtocolMapping convertMapping(ProtocolMappingPO po) {
        return McpToolProtocolConfigVO.ProtocolMapping.builder()
                .mappingType(po.getMappingType())
                .parentPath(po.getParentPath())
                .fieldName(po.getFieldName())
                .mcpPath(po.getMcpPath())
                .mcpType(po.getMcpType())
                .mcpDesc(po.getMcpDesc())
                .isRequired(po.getIsRequired())
                .sortOrder(po.getSortOrder())
                .build();
    }
}
