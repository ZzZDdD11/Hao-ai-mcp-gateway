package com.hao.ai.infrastructure.adapter.repository;

import com.hao.ai.domain.auth.adapter.repository.IAuthRepository;
import com.hao.ai.domain.auth.model.entity.LicenseCommandEntity;
import com.hao.ai.domain.auth.model.valobj.McpGatewayAuthVO;
import com.hao.ai.domain.auth.model.valobj.enums.AuthStatusEnum;
import com.hao.ai.infrastructure.dao.AuthDao;
import com.hao.ai.infrastructure.dao.GatewayDao;
import com.hao.ai.infrastructure.dao.po.AuthPO;
import com.hao.ai.infrastructure.dao.po.GatewayPO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;

/**
 * IAuthRepository 实现：鉴权配置的查询与写入。
 */
@Slf4j
@Repository
public class AuthRepository implements IAuthRepository {

    @Resource
    private GatewayDao gatewayDao;

    @Resource
    private AuthDao authDao;

    @Override
    public AuthStatusEnum.GatewayConfig queryGatewayAuthStatus(String gatewayId) {
        GatewayPO po = gatewayDao.queryByGatewayId(gatewayId);
        if (po == null || po.getAuth() == null || po.getAuth() == 0) {
            return AuthStatusEnum.GatewayConfig.NOT_VERIFIED;
        }
        return AuthStatusEnum.GatewayConfig.STRONG_VERIFIED;
    }

    @Override
    public McpGatewayAuthVO queryEffectiveGatewayAuthInfo(LicenseCommandEntity commandEntity) {
        AuthPO po = authDao.queryByGatewayIdAndApiKey(commandEntity.getGatewayId(), commandEntity.getApiKey());
        if (po == null) return null;

        return McpGatewayAuthVO.builder()
                .gatewayId(po.getGatewayId())
                .apiKey(po.getApiKey())
                .rateLimit(po.getRateLimit())
                .expireTime(po.getExpireTime() != null ? Timestamp.valueOf(po.getExpireTime()) : null)
                .status(po.getStatus() != null && po.getStatus() == 1
                        ? AuthStatusEnum.AuthConfig.ENABLE
                        : AuthStatusEnum.AuthConfig.DISABLE)
                .build();
    }

    @Override
    public void insert(McpGatewayAuthVO mcpGatewayAuthVO) {
        AuthPO po = new AuthPO();
        po.setGatewayId(mcpGatewayAuthVO.getGatewayId());
        po.setApiKey(mcpGatewayAuthVO.getApiKey());
        po.setRateLimit(mcpGatewayAuthVO.getRateLimit());
        if (mcpGatewayAuthVO.getExpireTime() != null) {
            po.setExpireTime(new Timestamp(mcpGatewayAuthVO.getExpireTime().getTime()).toLocalDateTime());
        }
        authDao.insert(po);
    }
}
