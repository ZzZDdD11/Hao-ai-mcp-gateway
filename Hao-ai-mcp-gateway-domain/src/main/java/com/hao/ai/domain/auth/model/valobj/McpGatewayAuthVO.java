package com.hao.ai.domain.auth.model.valobj;

import com.hao.ai.domain.auth.model.valobj.enums.AuthStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 网关鉴权配置值对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class McpGatewayAuthVO {

    /**
     * 网关 ID
     */
    private String gatewayId;

    /**
     * API Key
     */
    private String apiKey;

    /**
     * 限流值（次/小时）
     */
    private Integer rateLimit;

    /**
     * 过期时间（null 表示永久）
     */
    private Date expireTime;

    /**
     * 鉴权状态
     */
    private AuthStatusEnum.AuthConfig status;
}
