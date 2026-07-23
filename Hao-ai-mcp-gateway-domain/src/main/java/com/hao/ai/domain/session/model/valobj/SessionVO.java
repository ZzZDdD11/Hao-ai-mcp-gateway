package com.hao.ai.domain.session.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SessionVO {
    /**
     * 会话唯一标识符
     */
    private String sessionId;

    /**
     * SSE 信道（可选）
     * SSE transport 用此 sink 向客户端推送流式信息；
     * Streamable HTTP transport 无需长连接，该字段为 null
     */
    private Sinks.Many<ServerSentEvent<String>> sink;

    /**
     * 传输类型："sse" | "streamable_http"
     */
    private String transportType;
    /**
     * 会话创建时间
     */
    private Instant createTime;
    /**
     * 最后访问时间(心跳/消息交互式更新)
     */
    private volatile Instant lastAccessedTime;
    /**
     * 会话活跃状态标识
     */
    private volatile boolean active;

    public SessionVO(String sessionId, Sinks.Many<ServerSentEvent<String>> sink){
        this.sessionId = sessionId;
        this.sink = sink;
        this.transportType = "sse";
        this.createTime = Instant.now();
        this.lastAccessedTime = Instant.now();
        this.active = true;
    }

    /**
     * Streamable HTTP transport 专用构造（无 SSE sink）
     */
    public SessionVO(String sessionId){
        this.sessionId = sessionId;
        this.sink = null;
        this.transportType = "streamable_http";
        this.createTime = Instant.now();
        this.lastAccessedTime = Instant.now();
        this.active = true;
    }

    /**
     * 标记为非活跃状态
     */
    public void markInactive(){
        this.active = false;
    }

    /**
     * 更新最后访问时间
     */
    public void updateLastAccessed(){
        this.lastAccessedTime = Instant.now();
    }

    /**
     * 过期时间判断
     * @param timeoutMinutes
     * @return
     */
    public boolean isExpired(long timeoutMinutes){
        return lastAccessedTime.isBefore(Instant.now().minus(timeoutMinutes, ChronoUnit.MINUTES));
    }
}
