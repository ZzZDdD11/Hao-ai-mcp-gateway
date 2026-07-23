package com.hao.ai.infrastructure.dao.po;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * mcp_protocol_http 表持久化对象
 */
@Data
public class ProtocolHttpPO {
    private Long id;
    private Long protocolId;
    private String httpUrl;
    private String httpMethod;
    private String httpHeaders;
    private Integer timeout;
    private Integer retryTimes;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
