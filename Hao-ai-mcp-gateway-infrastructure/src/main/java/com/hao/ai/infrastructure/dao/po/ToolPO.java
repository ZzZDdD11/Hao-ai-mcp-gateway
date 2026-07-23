package com.hao.ai.infrastructure.dao.po;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * mcp_gateway_tool 表持久化对象
 */
@Data
public class ToolPO {
    private Long id;
    private String gatewayId;
    private Long toolId;
    private String toolName;
    private String toolType;
    private String toolDescription;
    private String toolVersion;
    private Long protocolId;
    private String protocolType;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
