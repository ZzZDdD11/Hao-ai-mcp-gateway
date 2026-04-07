package com.hao.ai.domain.session.service.impl;

import com.hao.ai.domain.session.ISessionManagementService;
import com.hao.ai.domain.session.model.valobj.SessionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SessionManagementService implements ISessionManagementService {

    private final Map<String, SessionVO> activeSessions = new ConcurrentHashMap<>();

    @Override
    public SessionVO createSession(String gatewayId, String apiKey) {
        log.info("开始创建会话 gatewayId:{}", gatewayId);

        String sessionId = UUID.randomUUID().toString();

        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().multicast().onBackpressureBuffer();

        // 发送端点信息，后续发到这进行通信
        String messageEndpoint = "/" + gatewayId + "/mcp/message?sessionId=" + sessionId;
        sink.tryEmitNext(ServerSentEvent.<String>builder()
                .event("endpoint")
                .data(messageEndpoint)
                .build());

        SessionVO sessionVO = new SessionVO(sessionId, sink);
        activeSessions.put(sessionId, sessionVO);
        log.info("创建会话 ");

        return sessionVO;
    }

    @Override
    public void removeSession(String sessionId) {

    }

    @Override
    public SessionVO getSession(String sessionId) {
        return null;
    }

    @Override
    public void cleanupExpiredSessions() {

    }

    @Override
    public void shutdown() {

    }
}
