package com.hao.ai.infrastructure.dao;

import com.hao.ai.infrastructure.dao.po.ToolPO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ToolDao {

    @Select("SELECT * FROM mcp_gateway_tool WHERE gateway_id = #{gatewayId} ORDER BY id DESC")
    List<ToolPO> queryByGatewayId(String gatewayId);

    @Select("SELECT * FROM mcp_gateway_tool WHERE gateway_id = #{gatewayId} AND tool_name = #{toolName}")
    ToolPO queryByGatewayIdAndToolName(@Param("gatewayId") String gatewayId, @Param("toolName") String toolName);

    @Insert("INSERT INTO mcp_gateway_tool (gateway_id, tool_id, tool_name, tool_type, tool_description, tool_version, protocol_id, protocol_type) " +
            "VALUES (#{gatewayId}, #{toolId}, #{toolName}, #{toolType}, #{toolDescription}, #{toolVersion}, #{protocolId}, #{protocolType})")
    int insert(ToolPO po);

    @Delete("DELETE FROM mcp_gateway_tool WHERE tool_id = #{toolId}")
    int deleteByToolId(Long toolId);
}
