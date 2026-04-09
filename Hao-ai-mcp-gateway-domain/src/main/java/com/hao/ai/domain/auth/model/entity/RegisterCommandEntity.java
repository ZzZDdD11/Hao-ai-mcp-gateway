package com.hao.ai.domain.auth.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 注册命令实体
 *
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegisterCommandEntity {

    /**
     * 网关ID
     */
    private String gatewayId;
    /**
     * 速率限制（次/小时）
     */
    private Integer rateLimit;
    /**
     * 过期时间
     */
    private Date expireTime;

}
