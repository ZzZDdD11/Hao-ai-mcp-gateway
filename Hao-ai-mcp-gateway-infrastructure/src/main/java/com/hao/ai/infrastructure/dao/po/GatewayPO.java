package com.hao.ai.infrastructure.dao.po;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * mcp_gateway 表持久化对象
 */
@Data
public class GatewayPO {
    private Long id;
    private String gatewayId;
    private String gatewayName;
    private String gatewayDesc;
    private String version;
    private Integer auth;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
