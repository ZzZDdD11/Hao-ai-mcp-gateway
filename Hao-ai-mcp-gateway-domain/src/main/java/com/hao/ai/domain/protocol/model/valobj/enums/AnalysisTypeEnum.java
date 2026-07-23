package com.hao.ai.domain.protocol.model.valobj.enums;

import com.alibaba.fastjson.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 协议解析类型枚举
 */
@Getter
@AllArgsConstructor
public enum AnalysisTypeEnum {

    OPENAPI("openapi", "OpenAPI 解析"),
    SWAGGER("swagger", "Swagger 解析"),
    ;

    private final String code;
    private final String info;

    /**
     * Swagger 解析策略选择：根据 operation 结构判断用 requestBody 还是 parameters 解析
     */
    @Getter
    @AllArgsConstructor
    public enum SwaggerAnalysisAction {

        REQUEST_BODY("requestBodyAnalysis", "请求体解析策略"),
        PARAMETERS("parametersAnalysis", "参数解析策略"),
        ;

        private final String code;
        private final String info;

        /**
         * 根据 operation 结构选择解析策略：有 requestBody 用 REQUEST_BODY，否则 PARAMETERS
         */
        public static SwaggerAnalysisAction get(JSONObject operation) {
            if (operation != null && operation.containsKey("requestBody")) {
                return REQUEST_BODY;
            }
            return PARAMETERS;
        }
    }
}
