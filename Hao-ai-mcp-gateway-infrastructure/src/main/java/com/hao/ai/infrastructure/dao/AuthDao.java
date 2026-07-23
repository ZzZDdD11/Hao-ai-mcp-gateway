package com.hao.ai.infrastructure.dao;

import com.hao.ai.infrastructure.dao.po.AuthPO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AuthDao {

    @Select("SELECT * FROM mcp_gateway_auth WHERE api_key = #{apiKey} AND status = 1")
    AuthPO queryByApiKey(String apiKey);

    @Select("SELECT * FROM mcp_gateway_auth WHERE gateway_id = #{gatewayId} AND status = 1 ORDER BY id DESC")
    List<AuthPO> queryByGatewayId(String gatewayId);

    @Insert("INSERT INTO mcp_gateway_auth (gateway_id, api_key, rate_limit, expire_time, status) " +
            "VALUES (#{gatewayId}, #{apiKey}, #{rateLimit}, #{expireTime}, 1)")
    int insert(AuthPO po);

    @Update("UPDATE mcp_gateway_auth SET status = 0 WHERE id = #{id}")
    int updateStatusToDisabled(Long id);
}
