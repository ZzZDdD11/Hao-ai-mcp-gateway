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
     * SSE信道
     * 服务端通过该 sink 向客户端推送流式信息
     */
    private Sinks.Many<ServerSentEvent<String>> sink;
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

    public  SessionVO(String sessionId, Sinks.Many<ServerSentEvent<String>> sink){
        this.sessionId = sessionId;
        this.sink = sink;
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
