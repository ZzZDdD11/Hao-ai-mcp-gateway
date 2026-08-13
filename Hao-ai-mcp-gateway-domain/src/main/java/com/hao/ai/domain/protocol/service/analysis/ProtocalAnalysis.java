package com.hao.ai.domain.protocol.service.analysis;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hao.ai.domain.protocol.model.entity.AnalysisCommandEntity;
import com.hao.ai.domain.protocol.model.valobj.enums.AnalysisTypeEnum;
import com.hao.ai.domain.protocol.model.valobj.http.HTTPProtocolVO;
import com.hao.ai.domain.protocol.service.IProtocolAnalysis;
import com.hao.ai.domain.protocol.service.analysis.strategy.IProtocalAnalysisStrategy;
import com.hao.ai.types.enums.ResponseCode;
import com.hao.ai.types.exception.AppException;
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
            if (null == root) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "OpenAPI JSON 解析失败，请检查格式");
            }

            // servers[0].url 作为 baseUrl；缺失时给出明确提示而非静默失败
            com.alibaba.fastjson.JSONArray servers = root.getJSONArray("servers");
            if (null == servers || servers.isEmpty() || null == servers.getJSONObject(0)) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "OpenAPI 缺少 servers 字段，请在 JSON 顶层添加 \"servers\":[{\"url\":\"http://后端服务地址\"}]");
            }
            String baseUrl = servers.getJSONObject(0).getString("url");
            if (null == baseUrl || baseUrl.isEmpty()) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "OpenAPI servers[0].url 为空，请填写后端服务地址");
            }

            // paths：所有接口定义
            JSONObject paths = root.getJSONObject("paths");
            if (null == paths || paths.isEmpty()) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "OpenAPI 缺少 paths 字段或 paths 为空");
            }
            // schemas：所有可复用的对象模型定义（可为空，不影响基础解析）
            JSONObject schemas = null;
            JSONObject components = root.getJSONObject("components");
            if (null != components) {
                schemas = components.getJSONObject("schemas");
            }

            // endpoints 为空时自动导入全部 paths，无需手填
            List<String> endpoints = commandEntity.getEndpoints();
            if (null == endpoints || endpoints.isEmpty()) {
                endpoints = new ArrayList<>(paths.keySet());
            }

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

                // 自动生成工具名（路径末段）与工具描述（operation.summary）
                vo.setToolName(buildToolName(endpoint));
                vo.setToolDescription(buildToolDescription(operation, method, endpoint));

                // 组装 MCP 映射信息（后续会被存入 mcp_protocol_mapping）
                List<HTTPProtocolVO.ProtocolMapping> mappings = new ArrayList<>();

                // 根据 operation 结构选择解析策略：requestBody 优先，否则 parameters
                AnalysisTypeEnum.SwaggerAnalysisAction analysisAction = AnalysisTypeEnum.SwaggerAnalysisAction.get(operation);
                IProtocalAnalysisStrategy strategy = protocolAnalysisStrategyMap.get(analysisAction.getCode());
                if (null != strategy) {
                    strategy.doAnalysis(operation, schemas, mappings);
                }

                vo.setMappings(mappings);
                list.add(vo);
            }

        } catch (AppException e) {
            log.error("协议解析参数错误 endpoints:{} info:{}", JSON.toJSONString(commandEntity.getEndpoints()), e.getInfo());
            throw e;
        } catch (Exception e) {
            log.error("协议解析失败 endpoints:{} openApiJson:{}", JSON.toJSONString(commandEntity.getEndpoints()), commandEntity.getOpenApiJson(), e);
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "协议解析失败：" + e.getMessage(), e);
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

    /**
     * 从接口路径生成工具名：取路径末段，如 /api/tools/query_bugs -> query_bugs
     */
    private String buildToolName(String endpoint) {
        if (null == endpoint) return "";
        String trimmed = endpoint.replaceAll("/+$", "");
        int idx = trimmed.lastIndexOf('/');
        String name = idx >= 0 ? trimmed.substring(idx + 1) : trimmed;
        return name.isEmpty() ? trimmed.replace("/", "_") : name;
    }

    /**
     * 生成工具描述：优先 operation.summary，其次 operationId，兜底 method + path
     */
    private String buildToolDescription(JSONObject operation, String method, String endpoint) {
        if (null != operation) {
            String summary = operation.getString("summary");
            if (null != summary && !summary.trim().isEmpty()) return summary.trim();
            String operationId = operation.getString("operationId");
            if (null != operationId && !operationId.trim().isEmpty()) return operationId.trim();
        }
        return method.toUpperCase() + " " + endpoint;
    }
}
