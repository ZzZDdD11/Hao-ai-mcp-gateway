package com.hao.ai.trigger.http;

import com.alibaba.fastjson.JSON;
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

            List<String> created = new ArrayList<>();
            List<String> skipped = new ArrayList<>();

            for (HTTPProtocolVO protocol : protocols) {
                String toolName = protocol.getToolName();
                if (null == toolName || toolName.trim().isEmpty()) {
                    skipped.add(protocol.getHttpUrl() + "(无法生成工具名)");
                    continue;
                }
                // 同网关同名工具已存在则跳过
                if (null != toolDao.queryByGatewayIdAndToolName(gatewayId, toolName)) {
                    skipped.add(toolName);
                    continue;
                }

                // 存 HTTP 协议配置
                Long protocolId = System.currentTimeMillis();
                ProtocolHttpPO httpPo = new ProtocolHttpPO();
                httpPo.setProtocolId(protocolId);
                httpPo.setHttpUrl(protocol.getHttpUrl());
                httpPo.setHttpMethod(protocol.getHttpMethod());
                httpPo.setHttpHeaders(protocol.getHttpHeaders());
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
            result.put("skipped", skipped);
            return Response.<Map<String, Object>>builder().code(SUCCESS_CODE).info(SUCCESS_INFO).data(result).build();
        } catch (AppException e) {
            return Response.<Map<String, Object>>builder().code(e.getCode()).info(e.getInfo()).build();
        } catch (Exception e) {
            log.error("import-bind 失败", e);
            return Response.<Map<String, Object>>builder().code("0001").info("导入失败：" + e.getMessage()).build();
        }
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
