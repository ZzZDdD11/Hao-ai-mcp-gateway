package com.hao.ai.trigger.http;

import com.hao.ai.api.IMcpGatewayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class McpGatewayController implements IMcpGatewayService {

    @Override
    public reactor.core.publisher.Flux<ServerSentEvent<String>> establishSSEConnection(String gatewayId) throws Exception {
        if(gatewayId == null || gatewayId.isEmpty()){

        }
        return null;
    }
}
