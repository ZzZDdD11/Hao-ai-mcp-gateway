package com.hao.ai.cases.mcp;

import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

public interface IMcpSessionService {
    Flux<ServerSentEvent<String>> CreateMcpSession(String gatewayId, String apikey) throws Exception;
}
