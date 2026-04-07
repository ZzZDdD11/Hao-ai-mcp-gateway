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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class SessionManagementService implements ISessionManagementService {

    private static final long SESSION_TIMEOUT_MINUTES = 30;

    /**
     * 定时任务调度
     */
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor();

    private final Map<String, SessionVO> activeSessions = new ConcurrentHashMap<>();

    public SessionManagementService() {
        cleanupScheduler.scheduleAtFixedRate(this::cleanupExpiredSessions, 5, 5, TimeUnit.MINUTES);
        log.info("会话管理服务已启动，会话超时时间: {} 分钟", SESSION_TIMEOUT_MINUTES);
    }

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
        log.info("删除会话配置 sessionId:{}", sessionId);
        SessionVO sessionVO = activeSessions.remove(sessionId);
        if (null == sessionVO) return;

        sessionVO.markInactive();

        try {
            sessionVO.getSink().tryEmitComplete();
        } catch (Exception e) {
            log.warn("关闭会话Sink时出错:{}", e.getMessage());
        }

        log.info("移除会话:{},剩余活跃会话数:{}", sessionId, activeSessions.size());
    }

    @Override
    public SessionVO getSession(String sessionId) {
        if (null == sessionId || sessionId.isEmpty()) {
            return null;
        }

        SessionVO sessionConfigVO = activeSessions.get(sessionId);
        if (null != sessionConfigVO && sessionConfigVO.isActive()) {
            sessionConfigVO.updateLastAccessed();
            return sessionConfigVO;
        }

        return null;
    }

    public void cleanupExpiredSessions() {
        int cleanedCount = 0;

        for (Map.Entry<String, SessionVO> entry : activeSessions.entrySet()) {
            SessionVO sessionConfigVO = entry.getValue();

            if (!sessionConfigVO.isActive() || sessionConfigVO.isExpired(SESSION_TIMEOUT_MINUTES)) {
                removeSession(sessionConfigVO.getSessionId());
                cleanedCount++;
            }

        }

        // 记录清理日志
        if (cleanedCount > 0) {
            log.info("清理了 {} 个过期会话，剩余活跃会话数: {}", cleanedCount, activeSessions.size());
        }
    }

    @Override
    public void shutdown() {
        log.info("关闭会话管理服务...");

        for (String sessionId : activeSessions.keySet()) {
            removeSession(sessionId);
        }

        // 关闭清理调度器
        cleanupScheduler.shutdown();

        try {
            // 等待5秒让正在执行的任务完成
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                // 超时强制关闭
                cleanupScheduler.shutdown();
            }
        } catch (InterruptedException e) {
            // 异常强制关闭
            cleanupScheduler.shutdown();
            Thread.currentThread().interrupt();
        }

        log.info("关闭会话管理服务完成");
    }

}
