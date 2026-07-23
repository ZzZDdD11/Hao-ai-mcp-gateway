package com.hao.ai.domain.session.model.valobj.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 网关基础配置值对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class McpGatewayConfigVO {
    private String gatewayId;
    private String gatewayName;
    private String version;
    private String gatewayDesc;
}
