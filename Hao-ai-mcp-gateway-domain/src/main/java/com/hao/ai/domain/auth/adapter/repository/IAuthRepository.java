package com.hao.ai.domain.auth.adapter.repository;

import com.hao.ai.domain.auth.model.entity.LicenseCommandEntity;
import com.hao.ai.domain.auth.model.valobj.McpGatewayAuthVO;
import com.hao.ai.domain.auth.model.valobj.enums.AuthStatusEnum;

/**
 * 鉴权领域仓库接口（infrastructure 层实现）
 */
public interface IAuthRepository {

    /**
     * 查询网关是否开启强校验
     */
    AuthStatusEnum.GatewayConfig queryGatewayAuthStatus(String gatewayId);

    /**
     * 查询有效的网关鉴权配置（限流/过期时间/状态）
     */
    McpGatewayAuthVO queryEffectiveGatewayAuthInfo(LicenseCommandEntity commandEntity);

    /**
     * 保存鉴权配置
     */
    void insert(McpGatewayAuthVO mcpGatewayAuthVO);
}
