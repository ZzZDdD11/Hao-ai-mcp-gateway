package com.hao.ai.domain.protocol.service.analysis;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hao.ai.domain.protocol.model.entity.AnalysisCommandEntity;
import com.hao.ai.domain.protocol.model.valobj.enums.AnalysisTypeEnum;
import com.hao.ai.domain.protocol.model.valobj.http.HTTPProtocolVO;
import com.hao.ai.domain.protocol.service.IProtocolAnalysis;
import com.hao.ai.domain.protocol.service.analysis.strategy.IProtocalAnalysisStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ProtocalAnalysis implements IProtocolAnalysis {
    /**
     * 解析策略集合（Spring 会将所有 IProtocolAnalysisStrategy 实现注入进来）。
     * key 来自 @Component("xxx") 的 beanName，与 AnalysisTypeEnum.SwaggerAnalysisAction.code 对应。
     */
    private final Map<String, IProtocalAnalysisStrategy> protocolAnalysisStrategyMap;

    @org.springframework.beans.factory.annotation.Autowired
    public ProtocalAnalysis(Map<String, IProtocalAnalysisStrategy> protocolAnalysisStrategyMap) {
        this.protocolAnalysisStrategyMap = protocolAnalysisStrategyMap;
    }

    /**
     * 将 OpenAPI/Swagger JSON 中指定 endpoints 解析为 HTTPProtocolVO 列表。
     *
     * 核心产出：
     * - httpUrl/httpMethod/httpHeaders/timeout：用于落库到 mcp_protocol_http
     * - mappings：用于落库到 mcp_protocol_mapping（并用于后续 tools/list 生成 inputSchema）
     *
     * 目前实现的解析范围（与策略实现保持一致）：
     * - requestBody(application/json + $ref) 对象入参
     * - parameters(in=query/path) 属性入参
     */
    @Override
    public List<HTTPProtocolVO> doAnalysis(AnalysisCommandEntity commandEntity) {
        log.info("协议解析请求 endpoints:{} openApiJson:{}", JSON.toJSONString(commandEntity.getEndpoints()), commandEntity.getOpenApiJson());

        // 解析结果：一个 endpoint 对应一个 HTTPProtocolVO
        List<HTTPProtocolVO> list = new ArrayList<>();
        try {
            // OpenAPI 根节点
            JSONObject root = JSON.parseObject(commandEntity.getOpenApiJson());

            // servers[0].url 作为 baseUrl（假设存在且可用）
            String baseUrl = root.getJSONArray("servers").getJSONObject(0).getString("url");

            // paths：所有接口定义；schemas：所有可复用的对象模型定义
            JSONObject paths = root.getJSONObject("paths");
            JSONObject schemas = root.getJSONObject("components").getJSONObject("schemas");

            List<String> endpoints = commandEntity.getEndpoints();
            if (null == endpoints || endpoints.isEmpty()) return list;

            for (String endpoint : endpoints) {
                // 找到 endpoint 对应的 pathItem（例如 /api/v1/xxx）
                JSONObject pathItem = paths.getJSONObject(endpoint);
                if (pathItem == null) continue;

                // 一个 pathItem 可能存在多个 method；当前实现按固定优先级选取一个
                String method = detectMethod(pathItem);
                JSONObject operation = pathItem.getJSONObject(method);

                // 组装 HTTP 协议基础信息
                HTTPProtocolVO vo = new HTTPProtocolVO();
                vo.setHttpUrl(baseUrl + endpoint);
                vo.setHttpMethod(method);
                vo.setHttpHeaders(JSON.toJSONString(new HashMap<>() {{
                    put("Content-Type", "application/json");
                }}));
                vo.setTimeout(30000);

                // 组装 MCP 映射信息（后续会被存入 mcp_protocol_mapping）
                List<HTTPProtocolVO.ProtocolMapping> mappings = new ArrayList<>();

                // 根据 operation 结构选择解析策略：requestBody 优先，否则 parameters
                AnalysisTypeEnum.SwaggerAnalysisAction analysisAction = AnalysisTypeEnum.SwaggerAnalysisAction.get(operation);
                IProtocalAnalysisStrategy strategy = protocolAnalysisStrategyMap.get(analysisAction.getCode());
                strategy.doAnalysis(operation, schemas, mappings);

                vo.setMappings(mappings);
                list.add(vo);
            }

        } catch (Exception e) {
            log.error("协议解析失败 endpoints:{} openApiJson:{}", JSON.toJSONString(commandEntity.getEndpoints()), commandEntity.getOpenApiJson(), e);
        }

        return list;
    }

    /**
     * 检测 HTTP method。
     * 当前逻辑：如果一个 endpoint 同时声明了多个 method，会按优先级返回第一个命中的 method。
     */
    private String detectMethod(JSONObject pathItem) {
        if (pathItem.containsKey("post")) return "post";
        if (pathItem.containsKey("get")) return "get";
        if (pathItem.containsKey("put")) return "put";
        if (pathItem.containsKey("delete")) return "delete";
        return "post";
    }
}
