package com.hao.ai.domain.auth.model.entity;

import com.hao.ai.domain.auth.model.valobj.McpSchemaVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LicenseCommandEntity {
    /**
     * 网关ID
     */
    private String gatewayId;

    /**
     * API密钥
     */
    private String apiKey;
}

