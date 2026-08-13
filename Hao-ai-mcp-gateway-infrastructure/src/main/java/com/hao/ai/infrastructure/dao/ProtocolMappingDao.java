package com.hao.ai.infrastructure.dao;

import com.hao.ai.infrastructure.dao.po.ProtocolMappingPO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ProtocolMappingDao {

    @Select("SELECT * FROM mcp_protocol_mapping WHERE protocol_id = #{protocolId} ORDER BY sort_order")
    List<ProtocolMappingPO> queryByProtocolId(Long protocolId);

    @Insert("<script>" +
            "INSERT INTO mcp_protocol_mapping (protocol_id, mapping_type, parent_path, field_name, mcp_path, mcp_type, mcp_desc, is_required, sort_order) " +
            "VALUES " +
            "<foreach collection='list' item='m' separator=','>" +
            "(#{m.protocolId}, #{m.mappingType}, #{m.parentPath}, #{m.fieldName}, #{m.mcpPath}, #{m.mcpType}, #{m.mcpDesc}, #{m.isRequired}, #{m.sortOrder})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("list") List<ProtocolMappingPO> list);

    @Delete("DELETE FROM mcp_protocol_mapping WHERE protocol_id = #{protocolId}")
    int deleteByProtocolId(@Param("protocolId") Long protocolId);
}
