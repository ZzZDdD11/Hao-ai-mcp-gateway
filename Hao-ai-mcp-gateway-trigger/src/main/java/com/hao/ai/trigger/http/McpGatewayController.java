package com.hao.ai.trigger.http;

import com.alibaba.fastjson.JSON;
import com.hao.ai.api.IMcpGatewayService;
import com.hao.ai.api.response.Response;
import com.hao.ai.cases.mcp.IMcpSessionService;
import com.hao.ai.cases.mcp.IMcpStreamableHttpService;
import com.hao.ai.domain.session.service.McpCoreHandlerImpl;
import com.hao.ai.types.enums.ResponseCode;
import com.hao.ai.types.exception.AppException;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.util.Objects;
import java.util.UUID;

@RestController
@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
@RequestMapping("/")
public class McpGatewayController implements IMcpGatewayService {


    @Resource
    private IMcpSessionService mcpSessionService;

    @Resource
    private IMcpStreamableHttpService mcpStreamableHttpService;

//    @Resource
//    private IMcpMessageService mcpMessageService;

    /**
     * 处理 sse 连接，创建会话
     * <br/>
     * <a href="http://localhost:8777/api-gateway/gateway_001/mcp/sse">http://localhost:8777/api-gateway/gateway_001/mcp/sse</a>
     * <br/>
     * <a href="http://localhost:8777/api-gateway/gateway_001/mcp/sse?api_key=gw-lf3HFzlJCdnrYl20oHbd5lJQxE7GWz8wjsSgjDZfctJNV8s5">http://localhost:8777/api-gateway/gateway_001/mcp/sse?api_key=gw-lf3HFzlJCdnrYl20oHbd5lJQxE7GWz8wjsSgjDZfctJNV8s5</a>
     *
     * @param gatewayId 网关ID
     */
    @GetMapping(value = "{gatewayId}/mcp/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Override
    public Flux<ServerSentEvent<String>> establishSSEConnection(
            @PathVariable("gatewayId") String gatewayId, @RequestParam("api_key") String apiKey) throws Exception {
        try {
            log.info("建立 MCP SSE 连接，gatewayId:{}", gatewayId);
            if (StringUtils.isBlank(gatewayId)) {
                log.info("非法参数，gateway is null");
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
            }

            return mcpSessionService.CreateMcpSession(gatewayId, apiKey);
        } catch (AppException e) {
            // 握手鉴权失败 → HTTP 401（不能以 200 返回 SSE 错误事件，否则扫描器会误判为无鉴权端点）
            if (McpCoreHandlerImpl.AUTH_FAILED_CODE.equals(e.getCode())) {
                log.warn("建立 MCP SSE 连接鉴权失败，gatewayId: {}", gatewayId);
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getInfo());
            }
            log.error("建立 MCP SSE 连接拒绝，gatewayId: {}", gatewayId, e);
            return Flux.just(ServerSentEvent.<String>builder()
                    .id(UUID.randomUUID().toString())
                    .event("error")
                    .data(JSON.toJSONString(Response.<String>builder()
                            .code(e.getCode())
                            .info(e.getInfo())
                            .build()))
                    .build());
        } catch (Exception e) {
            log.error("建立 MCP SSE 连接失败，gatewayId: {}", gatewayId, e);
            throw e;
        }
    }

    @PostMapping(value = "{gatewayId}/mcp/sse", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Object>> handleMessage(@PathVariable("gatewayId") String gatewayId,
                                                       @RequestParam String sessionId,
                                                      @RequestParam("api_key") String apiKey,
                                                      @RequestBody String messageBody){
        log.info("处理 MCP SSE 消息，gatewayId:{} apiKey:{} sessionId:{}", gatewayId, apiKey, sessionId);
        return Mono.fromCallable(() ->
                mcpSessionService.handleMessage(gatewayId, sessionId, apiKey, messageBody));
    }

    // ==================== Streamable HTTP Transport（MCP 2025-03-26）====================

    /**
     * POST /{gatewayId}/mcp — 发送 JSON-RPC 消息（Streamable HTTP 核心端点）
     * <p>
     * initialize 不带 Mcp-Session-Id，其他请求必须带。
     */
    @PostMapping(value = "{gatewayId}/mcp", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Object>> handleStreamablePost(
            @PathVariable("gatewayId") String gatewayId,
            @RequestHeader(value = "Mcp-Session-Id", required = false) String sessionId,
            @RequestParam(value = "api_key", required = false) String apiKey,
            @RequestBody String body) {
        log.info("Streamable HTTP POST gatewayId:{} sessionId:{}", gatewayId, sessionId);
        return Mono.fromCallable(() ->
                mcpStreamableHttpService.handleMessage(gatewayId, sessionId, apiKey, body));
    }

    /**
     * DELETE /{gatewayId}/mcp — 终止会话
     */
    @DeleteMapping("{gatewayId}/mcp")
    public Mono<ResponseEntity<Object>> handleStreamableDelete(
            @PathVariable("gatewayId") String gatewayId,
            @RequestHeader("Mcp-Session-Id") String sessionId) {
        log.info("Streamable HTTP DELETE gatewayId:{} sessionId:{}", gatewayId, sessionId);
        return Mono.fromCallable(() ->
                mcpStreamableHttpService.terminateSession(gatewayId, sessionId));
    }

    /**
     * GET /{gatewayId}/mcp — 可选，服务端推送 SSE 流
     * v1 不支持，返回 405 Method Not Allowed（规范允许）
     */
    @GetMapping(value = "{gatewayId}/mcp")
    public Mono<ResponseEntity<Object>> handleStreamableGet(
            @PathVariable("gatewayId") String gatewayId,
            @RequestHeader(value = "Mcp-Session-Id", required = false) String sessionId) {
        log.info("Streamable HTTP GET 暂不支持 gatewayId:{}", gatewayId);
        return Mono.just(ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build());
    }

}
