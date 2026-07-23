package com.hao.ai.infrastructure.dao.po;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * mcp_gateway_auth 表持久化对象
 */
@Data
public class AuthPO {
    private Long id;
    private String gatewayId;
    private String apiKey;
    private Integer rateLimit;
    private LocalDateTime expireTime;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
