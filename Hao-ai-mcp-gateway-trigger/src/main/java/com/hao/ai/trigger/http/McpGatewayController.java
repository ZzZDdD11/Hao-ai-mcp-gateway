package com.hao.ai.trigger.http;

import com.alibaba.fastjson.JSON;
import com.hao.ai.api.IMcpGatewayService;
import com.hao.ai.api.response.Response;
import com.hao.ai.cases.mcp.IMcpSessionService;
import com.hao.ai.types.enums.ResponseCode;
import com.hao.ai.types.exception.AppException;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
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
        try {
            log.info("处理 MCP SSE 消息，gatewayId:{} apiKey:{} sessionId:{} messageBody:{}", gatewayId, apiKey, sessionId, messageBody);


        } catch (Exception e){
            log.error("处理 MCP SSE 消息失败，gatewayId:{} sessionId:{} messageBody:{}", gatewayId, sessionId, messageBody, e);
            return Mono.empty();
        }
        return Mono.empty();

    }

}
