package com.hao.ai.infrastructure.dao.po;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * mcp_protocol_mapping 表持久化对象
 */
@Data
public class ProtocolMappingPO {
    private Long id;
    private Long protocolId;
    private String mappingType;
    private String parentPath;
    private String fieldName;
    private String mcpPath;
    private String mcpType;
    private String mcpDesc;
    private Integer isRequired;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
