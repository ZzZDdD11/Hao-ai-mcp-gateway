package com.hao.ai.domain.session.model.valobj.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 工具协议配置值对象
 * <p>
 * 包含 HTTP 转发信息（url/method/headers/timeout）和参数映射（mappings）。
 * tools/list 用 mappings 构建 inputSchema；tools/call 用 HTTP 信息转发请求。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class McpToolProtocolConfigVO {

    private String httpUrl;
    private String httpMethod;
    private String httpHeaders;
    private Integer timeout;

    /**
     * 请求参数映射列表（从 OpenAPI 解析而来）
     */
    private List<ProtocolMapping> requestProtocolMappings;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ProtocolMapping {
        /**
         * 映射类型：request-请求参数映射，response-响应数据映射
         */
        private String mappingType;
        /**
         * 父级路径（根节点为 null）
         */
        private String parentPath;
        /**
         * 字段名称
         */
        private String fieldName;
        /**
         * MCP 完整路径（如 xxxRequest01.city）
         */
        private String mcpPath;
        /**
         * MCP 数据类型：string/number/boolean/object/array
         */
        private String mcpType;
        /**
         * MCP 字段描述
         */
        private String mcpDesc;
        /**
         * 是否必填：0-否，1-是
         */
        private Integer isRequired;
        /**
         * 排序顺序
         */
        private Integer sortOrder;
    }
}
