package com.hao.ai.infrastructure.dao;

import com.hao.ai.infrastructure.dao.po.ProtocolHttpPO;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ProtocolHttpDao {

    @Select("SELECT * FROM mcp_protocol_http WHERE protocol_id = #{protocolId} AND status = 1")
    ProtocolHttpPO queryByProtocolId(Long protocolId);

    @Insert("INSERT INTO mcp_protocol_http (protocol_id, http_url, http_method, http_headers, timeout, retry_times, status) " +
            "VALUES (#{protocolId}, #{httpUrl}, #{httpMethod}, #{httpHeaders}, #{timeout}, #{retryTimes}, 1)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ProtocolHttpPO po);

    @Update("UPDATE mcp_protocol_http SET http_url = #{httpUrl}, http_method = #{httpMethod}, http_headers = #{httpHeaders}, timeout = #{timeout} WHERE protocol_id = #{protocolId}")
    int updateByProtocolId(ProtocolHttpPO po);
}
