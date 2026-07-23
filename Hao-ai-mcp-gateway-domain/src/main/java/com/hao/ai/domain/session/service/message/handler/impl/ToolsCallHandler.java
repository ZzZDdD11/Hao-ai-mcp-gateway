package com.hao.ai.domain.session.service.message.handler.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hao.ai.domain.session.adapter.repository.ISessionRepository;
import com.hao.ai.domain.session.model.valobj.McpSchemaVO;
import com.hao.ai.domain.session.model.valobj.gateway.McpToolProtocolConfigVO;
import com.hao.ai.domain.session.service.message.IRequestHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * tools/call 请求处理器：查工具 HTTP 配置 → 转发请求 → 封装 MCP 响应。
 * <p>
 * v1 参数映射简化策略：按 HTTP method 决定参数位置
 * - GET/DELETE → arguments 拼成 query string
 * - POST/PUT   → arguments 作为 JSON body
 * 后续可通过 ProtocolMapping.paramLocation 精确区分 query/path/body。
 */
@Slf4j
@Service("toolsCallHandler")
public class ToolsCallHandler implements IRequestHandler {

    @Resource
    private ISessionRepository repository;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public McpSchemaVO.JSONRPCResponse handle(String gatewayId, McpSchemaVO.JSONRPCRequest message) {
        log.info("消息处理服务-tools/call gatewayId:{}", gatewayId);

        // 1. 解析工具名和参数
        JSONObject params = JSON.parseObject(JSON.toJSONString(message.params()));
        String toolName = params.getString("name");
        Map<String, Object> arguments = params.getJSONObject("arguments");

        if (toolName == null || toolName.isEmpty()) {
            return buildErrorResponse(message.id(), -32602, "Missing tool name");
        }
        if (arguments == null) {
            arguments = new HashMap<>();
        }

        // 2. 查工具的 HTTP 协议配置
        McpToolProtocolConfigVO protocolConfig = repository.queryMcpGatewayProtocolConfig(gatewayId, toolName);
        if (protocolConfig == null || protocolConfig.getHttpUrl() == null) {
            return buildErrorResponse(message.id(), -32602, "Tool not found: " + toolName);
        }

        // 3. 转发 HTTP 请求并封装响应
        try {
            String responseBody = forwardHttp(protocolConfig, arguments);
            return buildToolResult(message.id(), responseBody, false);

        } catch (Exception e) {
            log.error("工具调用失败 gatewayId:{} toolName:{}", gatewayId, toolName, e);
            return buildToolResult(message.id(), "工具调用失败: " + e.getMessage(), true);
        }
    }

    /**
     * 根据 HTTP method 把 arguments 映射到请求中，发 HTTP 请求
     */
    private String forwardHttp(McpToolProtocolConfigVO config, Map<String, Object> arguments) throws Exception {
        String method = config.getHttpMethod() != null ? config.getHttpMethod().toUpperCase() : "GET";
        String url = config.getHttpUrl();
        int timeout = config.getTimeout() != null ? config.getTimeout() : 30000;

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .timeout(Duration.ofMillis(timeout));

        if ("GET".equals(method) || "DELETE".equals(method)) {
            // GET/DELETE：arguments 拼成 query string
            if (!arguments.isEmpty()) {
                String queryString = arguments.entrySet().stream()
                        .map(e -> e.getKey() + "=" + URLEncoder.encode(String.valueOf(e.getValue()), StandardCharsets.UTF_8))
                        .collect(Collectors.joining("&"));
                url = url + (url.contains("?") ? "&" : "?") + queryString;
            }
            builder.uri(URI.create(url));
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            // POST/PUT/PATCH：arguments 作为 JSON body
            builder.uri(URI.create(url));
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(JSON.toJSONString(arguments)));
        }

        // 设置协议配置中的额外 headers
        if (config.getHttpHeaders() != null && !config.getHttpHeaders().isEmpty()) {
            JSONObject headers = JSON.parseObject(config.getHttpHeaders());
            for (String key : headers.keySet()) {
                if (!"Content-Type".equalsIgnoreCase(key)) {
                    builder.header(key, headers.getString(key));
                }
            }
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        log.info("HTTP 转发 {} {} → status:{} bodyLen:{}", method, url, response.statusCode(), response.body().length());

        return response.body();
    }

    /**
     * 构建 MCP tools/call 结果响应
     *
     * @param id          请求 ID
     * @param text        HTTP 响应内容
     * @param isError     是否为错误
     */
    private McpSchemaVO.JSONRPCResponse buildToolResult(Object id, String text, boolean isError) {
        Map<String, Object> result = new HashMap<>();
        result.put("content", List.of(Map.of("type", "text", "text", text)));
        result.put("isError", isError);
        return new McpSchemaVO.JSONRPCResponse(McpSchemaVO.JSONRPC_VERSION, id, result, null);
    }

    private McpSchemaVO.JSONRPCResponse buildErrorResponse(Object id, int code, String message) {
        return new McpSchemaVO.JSONRPCResponse(
                McpSchemaVO.JSONRPC_VERSION,
                id,
                null,
                new McpSchemaVO.JSONRPCResponse.JSONRPCError(code, message, null)
        );
    }
}
