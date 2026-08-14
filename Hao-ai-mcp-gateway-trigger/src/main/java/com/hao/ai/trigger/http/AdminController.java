package com.hao.ai.trigger.http;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hao.ai.api.response.Response;
import com.hao.ai.domain.protocol.model.entity.AnalysisCommandEntity;
import com.hao.ai.domain.protocol.model.valobj.http.HTTPProtocolVO;
import com.hao.ai.domain.protocol.service.IProtocolAnalysis;
import com.hao.ai.infrastructure.dao.*;
import com.hao.ai.infrastructure.dao.po.*;
import com.hao.ai.types.exception.AppException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 管理后台 Controller — 网关/工具/协议/鉴权的 CRUD。
 * <p>
 * 管理后台是简单 CRUD，直接调 DAO；协议导入调 domain 层 IProtocolAnalysis 解析。
 */
@RestController
@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.DELETE, RequestMethod.OPTIONS})
@RequestMapping("/api/admin")
public class AdminController {

    private static final String SUCCESS_CODE = "0000";
    private static final String SUCCESS_INFO = "成功";

    @Resource private GatewayDao gatewayDao;
    @Resource private ToolDao toolDao;
    @Resource private AuthDao authDao;
    @Resource private ProtocolHttpDao protocolHttpDao;
    @Resource private ProtocolMappingDao protocolMappingDao;
    @Resource private IProtocolAnalysis protocalAnalysis;

    // ==================== 网关管理 ====================

    @GetMapping("/gateway/list")
    public Response<List<GatewayPO>> gatewayList() {
        return Response.<List<GatewayPO>>builder()
                .code(SUCCESS_CODE).info(SUCCESS_INFO)
                .data(gatewayDao.queryAll())
                .build();
    }

    @PostMapping("/gateway/config")
    public Response<Boolean> gatewayConfig(@RequestParam("gatewayId") String gatewayId,
                                           @RequestParam("gatewayName") String gatewayName,
                                           @RequestParam(value = "gatewayDesc", required = false) String gatewayDesc,
                                           @RequestParam(value = "version", required = false) String version) {
        GatewayPO po = new GatewayPO();
        po.setGatewayId(gatewayId);
        po.setGatewayName(gatewayName);
        po.setGatewayDesc(gatewayDesc);
        po.setVersion(version != null ? version : "1.0.0");
        po.setAuth(0);
        gatewayDao.insert(po);
        log.info("创建网关 gatewayId:{}", gatewayId);
        return ok();
    }

    @DeleteMapping("/gateway/{gatewayId}")
    public Response<Boolean> deleteGateway(@PathVariable String gatewayId) {
        gatewayDao.updateStatusToDisabled(gatewayId);
        log.info("禁用网关 gatewayId:{}", gatewayId);
        return ok();
    }

    /**
     * 配置上游工具端鉴权 token。
     * 导入工具时若网关配置了 token，会自动把 Authorization: Bearer <token> 写入 http_headers，
     * 供转发调用上游服务时携带。
     */
    @PostMapping("/gateway/upstream-token")
    public Response<Boolean> configUpstreamToken(@RequestParam("gatewayId") String gatewayId,
                                                 @RequestParam("upstreamToken") String upstreamToken) {
        gatewayDao.updateUpstreamToken(gatewayId, upstreamToken);
        log.info("配置上游 token gatewayId:{}", gatewayId);
        return ok();
    }

    // ==================== 工具管理 ====================

    @GetMapping("/tool/list")
    public Response<List<ToolPO>> toolList(@RequestParam("gatewayId") String gatewayId) {
        return Response.<List<ToolPO>>builder()
                .code(SUCCESS_CODE).info(SUCCESS_INFO)
                .data(toolDao.queryByGatewayId(gatewayId))
                .build();
    }

    @PostMapping("/tool")
    public Response<Boolean> createTool(@RequestBody Map<String, Object> body) {
        ToolPO po = new ToolPO();
        po.setGatewayId((String) body.get("gatewayId"));
        po.setToolName((String) body.get("toolName"));
        po.setToolDescription((String) body.get("toolDescription"));
        po.setToolType("function");
        po.setToolVersion("1.0.0");
        po.setProtocolId(Long.valueOf(body.get("protocolId").toString()));
        po.setProtocolType("http");
        po.setToolId(System.currentTimeMillis());
        toolDao.insert(po);
        log.info("创建工具 gatewayId:{} toolName:{}", po.getGatewayId(), po.getToolName());
        return ok();
    }

    @DeleteMapping("/tool/{toolId}")
    public Response<Boolean> deleteTool(@PathVariable Long toolId) {
        toolDao.deleteByToolId(toolId);
        log.info("删除工具 toolId:{}", toolId);
        return ok();
    }

    // ==================== 鉴权管理 ====================

    @GetMapping("/auth/list")
    public Response<List<AuthPO>> authList(@RequestParam("gatewayId") String gatewayId) {
        return Response.<List<AuthPO>>builder()
                .code(SUCCESS_CODE).info(SUCCESS_INFO)
                .data(authDao.queryByGatewayId(gatewayId))
                .build();
    }

    @PostMapping("/auth")
    public Response<String> createAuth(@RequestParam("gatewayId") String gatewayId,
                                       @RequestParam(value = "rateLimit", defaultValue = "1000") Integer rateLimit) {
        String apiKey = "gw-" + UUID.randomUUID().toString().replace("-", "");
        AuthPO po = new AuthPO();
        po.setGatewayId(gatewayId);
        po.setApiKey(apiKey);
        po.setRateLimit(rateLimit);
        po.setExpireTime(LocalDateTime.now().plusYears(1));
        authDao.insert(po);
        log.info("生成 API Key gatewayId:{} rateLimit:{}", gatewayId, rateLimit);
        return Response.<String>builder().code(SUCCESS_CODE).info(SUCCESS_INFO).data(apiKey).build();
    }

    @DeleteMapping("/auth/{id}")
    public Response<Boolean> deleteAuth(@PathVariable Long id) {
        authDao.updateStatusToDisabled(id);
        log.info("吊销 API Key id:{}", id);
        return ok();
    }

    // ==================== 协议导入 ====================

    @PostMapping("/protocol/import")
    public Response<List<Long>> importProtocol(@RequestBody Map<String, Object> body) {
        String openApiJson = JSON.toJSONString(body.get("openApiJson"));
        @SuppressWarnings("unchecked")
        List<String> endpoints = (List<String>) body.get("endpoints");

        // 调 domain 层解析 Swagger/OpenAPI
        AnalysisCommandEntity command = AnalysisCommandEntity.builder()
                .openApiJson(openApiJson)
                .endpoints(endpoints)
                .build();
        List<HTTPProtocolVO> protocols = protocalAnalysis.doAnalysis(command);

        // 存库：每个 HTTPProtocolVO → mcp_protocol_http + mcp_protocol_mapping
        List<Long> protocolIds = new ArrayList<>();
        for (HTTPProtocolVO protocol : protocols) {
            Long protocolId = System.currentTimeMillis();

            // 存 HTTP 配置
            ProtocolHttpPO httpPo = new ProtocolHttpPO();
            httpPo.setProtocolId(protocolId);
            httpPo.setHttpUrl(protocol.getHttpUrl());
            httpPo.setHttpMethod(protocol.getHttpMethod());
            httpPo.setHttpHeaders(protocol.getHttpHeaders());
            httpPo.setTimeout(protocol.getTimeout());
            httpPo.setRetryTimes(0);
            protocolHttpDao.insert(httpPo);

            // 存参数映射（批量）
            List<ProtocolMappingPO> mappingPOs = protocol.getMappings().stream()
                    .map(m -> convertMappingToPO(m, protocolId))
                    .collect(Collectors.toList());
            if (!mappingPOs.isEmpty()) {
                protocolMappingDao.batchInsert(mappingPOs);
            }

            protocolIds.add(protocolId);
            log.info("协议导入 protocolId:{} url:{} mappings:{}", protocolId, protocol.getHttpUrl(), mappingPOs.size());
        }

        return Response.<List<Long>>builder().code(SUCCESS_CODE).info(SUCCESS_INFO).data(protocolIds).build();
    }

    /**
     * 仅解析 OpenAPI，不落库。返回接口清单（含自动生成的工具名/描述），供前端预览勾选。
     */
    @PostMapping("/protocol/parse")
    public Response<List<Map<String, Object>>> parseProtocol(@RequestBody Map<String, Object> body) {
        String openApiJson = JSON.toJSONString(body.get("openApiJson"));
        @SuppressWarnings("unchecked")
        List<String> endpoints = (List<String>) body.get("endpoints");
        try {
            AnalysisCommandEntity command = AnalysisCommandEntity.builder()
                    .openApiJson(openApiJson).endpoints(endpoints).build();
            List<HTTPProtocolVO> protocols = protocalAnalysis.doAnalysis(command);
            List<Map<String, Object>> list = protocols.stream().map(p -> {
                Map<String, Object> m = new HashMap<>();
                m.put("httpUrl", p.getHttpUrl());
                m.put("httpMethod", p.getHttpMethod());
                m.put("toolName", p.getToolName());
                m.put("toolDescription", p.getToolDescription());
                return m;
            }).collect(Collectors.toList());
            return Response.<List<Map<String, Object>>>builder().code(SUCCESS_CODE).info(SUCCESS_INFO).data(list).build();
        } catch (AppException e) {
            return Response.<List<Map<String, Object>>>builder().code(e.getCode()).info(e.getInfo()).data(new ArrayList<>()).build();
        } catch (Exception e) {
            log.error("协议解析失败", e);
            return Response.<List<Map<String, Object>>>builder().code("0001").info("解析失败：" + e.getMessage()).data(new ArrayList<>()).build();
        }
    }

    /**
     * 一键导入并绑定：解析 OpenAPI → 存协议 → 自动创建工具绑定到指定网关。
     * 同网关下同名工具已存在则跳过（标记 skipped），不中断。
     */
    @PostMapping("/protocol/import-bind")
    public Response<Map<String, Object>> importBindProtocol(@RequestBody Map<String, Object> body) {
        String openApiJson = JSON.toJSONString(body.get("openApiJson"));
        String gatewayId = (String) body.get("gatewayId");
        @SuppressWarnings("unchecked")
        List<String> endpoints = (List<String>) body.get("endpoints");

        if (null == gatewayId || gatewayId.trim().isEmpty()) {
            return Response.<Map<String, Object>>builder().code("0002").info("请选择目标网关").build();
        }

        try {
            AnalysisCommandEntity command = AnalysisCommandEntity.builder()
                    .openApiJson(openApiJson).endpoints(endpoints).build();
            List<HTTPProtocolVO> protocols = protocalAnalysis.doAnalysis(command);
            Map<String, Object> result = doImportBind(gatewayId, protocols);
            return Response.<Map<String, Object>>builder().code(SUCCESS_CODE).info(SUCCESS_INFO).data(result).build();
        } catch (AppException e) {
            return Response.<Map<String, Object>>builder().code(e.getCode()).info(e.getInfo()).build();
        } catch (Exception e) {
            log.error("import-bind 失败", e);
            return Response.<Map<String, Object>>builder().code("0001").info("导入失败：" + e.getMessage()).build();
        }
    }

    /**
     * 按服务 URL 自动导入：后端探测 OpenAPI 端点 → 抓取 → 解析 → 建工具绑定网关。
     * <p>
     * 入参：{ "gatewayId": "pm-dashboard", "serviceUrl": "http://xxx:8000" }
     * serviceUrl 支持两种形式：
     *  - 服务根地址（如 http://xxx:8000），后端自动按约定探测 OpenAPI 端点；
     *  - 完整 OpenAPI 地址（以 openapi.json / swagger.json / api-docs 结尾），直接抓取。
     */
    @PostMapping("/protocol/import-bind-from-url")
    public Response<Map<String, Object>> importBindFromUrl(@RequestBody Map<String, Object> body) {
        String gatewayId = (String) body.get("gatewayId");
        String serviceUrl = (String) body.get("serviceUrl");

        if (null == gatewayId || gatewayId.trim().isEmpty()) {
            return Response.<Map<String, Object>>builder().code("0002").info("请选择目标网关").build();
        }
        if (null == serviceUrl || serviceUrl.trim().isEmpty()) {
            return Response.<Map<String, Object>>builder().code("0002").info("请填写服务地址").build();
        }

        try {
            String openApiJson = fetchOpenApiJson(serviceUrl.trim());
            AnalysisCommandEntity command = AnalysisCommandEntity.builder()
                    .openApiJson(openApiJson).endpoints(null).build();
            List<HTTPProtocolVO> protocols = protocalAnalysis.doAnalysis(command);
            Map<String, Object> result = doImportBind(gatewayId, protocols);
            return Response.<Map<String, Object>>builder().code(SUCCESS_CODE).info(SUCCESS_INFO).data(result).build();
        } catch (AppException e) {
            return Response.<Map<String, Object>>builder().code(e.getCode()).info(e.getInfo()).build();
        } catch (Exception e) {
            log.error("import-bind-from-url 失败 gatewayId:{} serviceUrl:{}", gatewayId, serviceUrl, e);
            return Response.<Map<String, Object>>builder().code("0001").info("导入失败：" + e.getMessage()).build();
        }
    }

    /**
     * 落库（upsert）：遍历解析出的协议，逐个写入 HTTP 配置 + 参数映射 + 工具绑定网关。
     * <p>
     * - 同名工具不存在 → 新建工具（created）；
     * - 同名工具已存在 → 复用旧 protocolId，更新 http 配置、重建参数映射、更新工具描述（updated），
     *   避免工具端改动后网关侧仍是旧配置。
     */
    private Map<String, Object> doImportBind(String gatewayId, List<HTTPProtocolVO> protocols) {
        List<String> created = new ArrayList<>();
        List<String> updated = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        // 网关配置的上游工具端 token，导入时注入 Authorization 头
        GatewayPO gateway = gatewayDao.queryByGatewayId(gatewayId);
        String upstreamToken = gateway != null ? gateway.getUpstreamToken() : null;

        for (HTTPProtocolVO protocol : protocols) {
            String toolName = protocol.getToolName();
            if (null == toolName || toolName.trim().isEmpty()) {
                skipped.add(protocol.getHttpUrl() + "(无法生成工具名)");
                continue;
            }

            ToolPO existing = toolDao.queryByGatewayIdAndToolName(gatewayId, toolName);
            if (null != existing) {
                // ===== 更新分支：复用旧 protocolId，同步最新配置 =====
                Long protocolId = existing.getProtocolId();

                // 1. 更新 HTTP 协议配置
                ProtocolHttpPO httpPo = new ProtocolHttpPO();
                httpPo.setProtocolId(protocolId);
                httpPo.setHttpUrl(protocol.getHttpUrl());
                httpPo.setHttpMethod(protocol.getHttpMethod());
                httpPo.setHttpHeaders(mergeUpstreamAuth(protocol.getHttpHeaders(), upstreamToken));
                httpPo.setTimeout(protocol.getTimeout());
                protocolHttpDao.updateByProtocolId(httpPo);

                // 2. 重建参数映射：删旧插新
                protocolMappingDao.deleteByProtocolId(protocolId);
                List<ProtocolMappingPO> mappingPOs = protocol.getMappings().stream()
                        .map(m -> convertMappingToPO(m, protocolId)).collect(Collectors.toList());
                if (!mappingPOs.isEmpty()) {
                    protocolMappingDao.batchInsert(mappingPOs);
                }

                // 3. 更新工具描述（协议 ID 不变）
                toolDao.updateDescriptionAndProtocolId(gatewayId, toolName, protocol.getToolDescription(), protocolId);
                updated.add(toolName);
                log.info("import-bind 更新工具 gatewayId:{} toolName:{} protocolId:{}", gatewayId, toolName, protocolId);
                continue;
            }

            // ===== 新建分支 =====
            Long protocolId = System.currentTimeMillis();
            ProtocolHttpPO httpPo = new ProtocolHttpPO();
            httpPo.setProtocolId(protocolId);
            httpPo.setHttpUrl(protocol.getHttpUrl());
            httpPo.setHttpMethod(protocol.getHttpMethod());
            httpPo.setHttpHeaders(mergeUpstreamAuth(protocol.getHttpHeaders(), upstreamToken));
            httpPo.setTimeout(protocol.getTimeout());
            httpPo.setRetryTimes(0);
            protocolHttpDao.insert(httpPo);

            List<ProtocolMappingPO> mappingPOs = protocol.getMappings().stream()
                    .map(m -> convertMappingToPO(m, protocolId)).collect(Collectors.toList());
            if (!mappingPOs.isEmpty()) {
                protocolMappingDao.batchInsert(mappingPOs);
            }

            // 创建工具绑定网关
            ToolPO toolPo = new ToolPO();
            toolPo.setGatewayId(gatewayId);
            toolPo.setToolName(toolName);
            toolPo.setToolDescription(protocol.getToolDescription());
            toolPo.setToolType("function");
            toolPo.setToolVersion("1.0.0");
            toolPo.setProtocolId(protocolId);
            toolPo.setProtocolType("http");
            toolPo.setToolId(System.currentTimeMillis());
            toolDao.insert(toolPo);
            created.add(toolName);
            log.info("import-bind 创建工具 gatewayId:{} toolName:{} protocolId:{}", gatewayId, toolName, protocolId);

            // 避免 protocolId/toolId 同毫秒重复
            try { Thread.sleep(2); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("created", created);
        result.put("updated", updated);
        result.put("skipped", skipped);
        return result;
    }

    /** OpenAPI 端点探测优先级：网关专用文档 → 标准 OpenAPI → Swagger 端点 */
    private static final List<String> OPENAPI_PROBE_PATHS = List.of(
            "/api/tools/openapi.json",
            "/openapi.json",
            "/v3/api-docs",
            "/v2/api-docs",
            "/swagger/v1/swagger.json",
            "/api-docs"
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * 抓取 OpenAPI JSON：校验 SSRF 目标 → 探测/直取 → 校验返回为 OpenAPI 文档。
     */
    private String fetchOpenApiJson(String serviceUrl) throws Exception {
        // SSRF 防护：仅允许 http/https，拒绝云元数据等危险地址
        URI uri = validateAndParseUrl(serviceUrl);

        // 若 URL 本身指向文档文件（openapi.json / swagger.json / api-docs），直接抓取
        String path = uri.getPath() != null ? uri.getPath() : "";
        if (path.endsWith(".json") || path.contains("api-docs") || path.contains("swagger")) {
            return fetchJson(uri.toString());
        }

        // 否则按约定探测 OpenAPI 端点
        String base = serviceUrl.replaceAll("/+$", "");
        String lastError = "";
        for (String probe : OPENAPI_PROBE_PATHS) {
            String url = base + probe;
            try {
                String body = fetchJson(url);
                JSONObject obj = JSON.parseObject(body);
                if (obj != null && (obj.containsKey("paths") || obj.containsKey("openapi") || obj.containsKey("swagger"))) {
                    log.info("探测到 OpenAPI 端点:{}", url);
                    return body;
                }
            } catch (Exception e) {
                lastError = e.getMessage();
            }
        }
        throw new AppException("无法从 " + base + " 自动发现 OpenAPI（最后错误：" + lastError + "），请改用 /protocol/import-bind 手动粘贴 JSON");
    }

    /**
     * 校验 URL 并解析为 URI，防止 SSRF。
     * 说明：本网关工具场景下服务多部署于内网（如 21.*、devcloud 域名），故允许内网地址；
     * 仅做基础防护：限制协议为 http/https、拒绝云元数据地址与空主机。
     */
    private URI validateAndParseUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            throw new AppException("服务地址格式非法");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new AppException("仅支持 http/https 协议的服务地址");
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new AppException("服务地址缺少主机名");
        }
        // 拒绝云元数据 / 链路本地地址，防止被用于读取实例凭据
        if ("169.254.169.254".equals(host) || "metadata.google.internal".equals(host)
                || host.equals("127.0.0.1") || host.equals("localhost")) {
            throw new AppException("禁止访问该地址");
        }
        return uri;
    }

    private String fetchJson(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new AppException("抓取失败 HTTP " + response.statusCode() + "：" + url);
        }
        return response.body();
    }

    @GetMapping("/protocol/list")
    public Response<List<ToolPO>> protocolList(@RequestParam String gatewayId) {
        // 协议列表通过工具列表间接展示（工具关联了 protocolId）
        return Response.<List<ToolPO>>builder()
                .code(SUCCESS_CODE).info(SUCCESS_INFO)
                .data(toolDao.queryByGatewayId(gatewayId))
                .build();
    }

    // ==================== 辅助方法 ====================

    /**
     * 把上游工具端 token 合并进请求头：若网关配置了 token，注入 Authorization: Bearer <token>。
     * 保留原有的 Content-Type 等头。
     */
    private String mergeUpstreamAuth(String originalHeaders, String upstreamToken) {
        JSONObject headers = new JSONObject();
        if (null != originalHeaders && !originalHeaders.trim().isEmpty()) {
            try {
                headers = JSON.parseObject(originalHeaders);
            } catch (Exception e) {
                log.warn("解析 http_headers 失败，忽略原始头:{}", originalHeaders);
                headers = new JSONObject();
            }
        }
        if (null != upstreamToken && !upstreamToken.trim().isEmpty()) {
            headers.put("Authorization", "Bearer " + upstreamToken.trim());
        }
        return JSON.toJSONString(headers);
    }

    private ProtocolMappingPO convertMappingToPO(HTTPProtocolVO.ProtocolMapping m, Long protocolId) {
        ProtocolMappingPO po = new ProtocolMappingPO();
        po.setProtocolId(protocolId);
        po.setMappingType(m.getMappingType());
        po.setParentPath(m.getParentPath());
        po.setFieldName(m.getFieldName());
        po.setMcpPath(m.getMcpPath());
        po.setMcpType(m.getMcpType());
        po.setMcpDesc(m.getMcpDesc());
        po.setIsRequired(m.getIsRequired());
        po.setSortOrder(m.getSortOrder());
        return po;
    }

    private Response<Boolean> ok() {
        return Response.<Boolean>builder().code(SUCCESS_CODE).info(SUCCESS_INFO).data(true).build();
    }
}
