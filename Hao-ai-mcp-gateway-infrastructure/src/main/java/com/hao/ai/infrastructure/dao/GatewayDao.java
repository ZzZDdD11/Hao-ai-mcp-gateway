package com.hao.ai.infrastructure.dao;

import com.hao.ai.infrastructure.dao.po.GatewayPO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface GatewayDao {

    @Select("SELECT * FROM mcp_gateway WHERE gateway_id = #{gatewayId} AND status = 1")
    GatewayPO queryByGatewayId(String gatewayId);

    @Select("SELECT * FROM mcp_gateway WHERE status = 1 ORDER BY id DESC")
    List<GatewayPO> queryAll();

    @Insert("INSERT INTO mcp_gateway (gateway_id, gateway_name, gateway_desc, version, auth, status) " +
            "VALUES (#{gatewayId}, #{gatewayName}, #{gatewayDesc}, #{version}, #{auth}, 1)")
    int insert(GatewayPO po);

    @Update("UPDATE mcp_gateway SET status = 0 WHERE gateway_id = #{gatewayId}")
    int updateStatusToDisabled(String gatewayId);
}
