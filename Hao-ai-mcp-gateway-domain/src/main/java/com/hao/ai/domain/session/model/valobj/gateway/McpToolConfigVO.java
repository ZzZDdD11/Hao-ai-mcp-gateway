package com.hao.ai.domain.session.model.valobj.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 网关工具配置值对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class McpToolConfigVO {
    private String toolId;
    private String toolName;
    private String toolDescription;
    private McpToolProtocolConfigVO mcpToolProtocolConfigVO;
}
