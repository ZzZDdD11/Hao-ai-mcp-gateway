package com.hao.ai.domain.protocol.service.analysis.strategy.impl;

import com.alibaba.fastjson.JSONObject;
import com.hao.ai.domain.protocol.model.valobj.http.HTTPProtocolVO;
import com.hao.ai.domain.protocol.service.analysis.strategy.AbstractProtocalAnalysisStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component("requestBodyAnalysis")
@Order(1)
public class RequestBodyAnalysisStrategy extends AbstractProtocalAnalysisStrategy { /**
 * 解析 requestBody 形式的入参。
 *
 * 目前仅处理：content.application/json.schema.$ref
 * - schema 不是 $ref（例如 inline object）时，当前实现会直接跳过
 * - content 不是 application/json 时，当前实现会直接跳过
 */
@Override
public void doAnalysis(JSONObject operation, JSONObject definitions, List<HTTPProtocolVO.ProtocolMapping> mappings) {
    // 1) 读取 requestBody
    JSONObject requestBody = operation.getJSONObject("requestBody");
    if (requestBody == null) return;

    // 2) 读取 application/json schema
    JSONObject content = requestBody.getJSONObject("content");
    JSONObject appJson = content.getJSONObject("application/json");
    if (appJson == null) return;

    JSONObject schema = appJson.getJSONObject("schema");
    String ref = schema.getString("$ref");

    // 3) 以 $ref 指向的 schema 作为根对象，生成根 mapping + 递归展开 properties
    if (ref != null) {
        String refName = ref.substring(ref.lastIndexOf('/') + 1);
        JSONObject reqSchema = definitions.getJSONObject(refName);

        // 根节点命名：将 Schema 名首字母小写，作为 MCP 根对象路径
        String rootName = toLowerCamel(refName);

        // 根 mapping：parentPath=null 表示根节点，mcpPath=rootName 表示对象根路径
        HTTPProtocolVO.ProtocolMapping rootMapping = HTTPProtocolVO.ProtocolMapping.builder()
                .mappingType("request")
                .parentPath(null)
                .fieldName(rootName)
                .mcpPath(rootName)
                .mcpType(convertType(reqSchema.getString("type")))
                .mcpDesc(reqSchema.getString("description"))
                .isRequired(1)
                .sortOrder(1)
                .build();

        mappings.add(rootMapping);

        // 递归展开：会生成形如 rootName.city / rootName.company.name 的 mcpPath
        parseProperties(rootName, reqSchema.getJSONObject("properties"), reqSchema.getJSONArray("required"), definitions, mappings);
    }
}

}
