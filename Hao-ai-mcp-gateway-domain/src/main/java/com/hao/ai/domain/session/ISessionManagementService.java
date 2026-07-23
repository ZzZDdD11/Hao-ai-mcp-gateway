package com.hao.ai.domain.session;
import com.hao.ai.domain.session.model.valobj.SessionVO;

public interface ISessionManagementService {
    /**
     * 创建会话（SSE transport，带 SSE sink）
     * @return 会话配置
     */
    SessionVO createSession(String gatewayId, String apiKey);

    /**
     * 创建会话（Streamable HTTP transport，无 SSE sink）
     * @param gatewayId 网关 ID
     * @return 会话配置
     */
    SessionVO createStreamableSession(String gatewayId);

    /**
     * 删除回话
     * @param sessionId 会话ID
     */
    void removeSession(String sessionId);

    /**
     * 获取会话
     * @param sessionId 会话ID
     * @return 会话配置
     */
    SessionVO getSession(String sessionId);

    /**
     * 清理过期会话
     */
    void cleanupExpiredSessions();

    /**
     * 关闭服务时，清理资源使用
     */
    void shutdown();
}
