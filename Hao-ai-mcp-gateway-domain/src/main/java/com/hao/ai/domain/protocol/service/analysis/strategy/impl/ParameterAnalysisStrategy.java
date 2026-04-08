package com.hao.ai.domain.protocol.service.analysis.strategy.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hao.ai.domain.protocol.model.valobj.http.HTTPProtocolVO;
import com.hao.ai.domain.protocol.service.analysis.strategy.AbstractProtocalAnalysisStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
@Slf4j
@Component("parametersAnalysis")
@Order(2)
public class ParameterAnalysisStrategy extends AbstractProtocalAnalysisStrategy {
    /**
     * 解析 parameters 形式的入参。
     *
     * 当前仅处理：in=query/path
     * - header/cookie 参数不在本策略处理范围内
     *
     * 输出形态：
     * - 标量参数：一条 mapping（mcpPath=name）
     * - $ref 参数：一条根 mapping（mcpPath=name）+ 递归展开 properties（mcpPath=name.xxx）
     */
    @Override
    public void doAnalysis(JSONObject operation, JSONObject definitions, List<HTTPProtocolVO.ProtocolMapping> mappings) {
        JSONArray parameters = operation.getJSONArray("parameters");
        if (parameters == null) return;

        for (int i = 0; i < parameters.size(); i++) {
            JSONObject param = parameters.getJSONObject(i);

            // 只解析 query/path 参数
            String in = param.getString("in");
            if (!"query".equals(in) && !"path".equals(in)) continue;

            String name = param.getString("name");
            boolean required = param.getBooleanValue("required");
            String description = param.getString("description");

            JSONObject schema = param.getJSONObject("schema");
            String type = schema.getString("type");
            String ref = schema.getString("$ref");

            // 参数为对象引用：先生成根节点，再递归展开子属性
            if (ref != null) {
                String refName = ref.substring(ref.lastIndexOf('/') + 1);
                JSONObject reqSchema = definitions.getJSONObject(refName);

                // 优先使用参数自身描述；如果未声明 type/description，则从引用对象补齐
                if (type == null) type = reqSchema.getString("type");
                if (description == null) description = reqSchema.getString("description");

                HTTPProtocolVO.ProtocolMapping rootMapping = HTTPProtocolVO.ProtocolMapping.builder()
                        .mappingType("request")
                        .parentPath(null)
                        .fieldName(name)
                        .mcpPath(name)
                        .mcpType(convertType(type))
                        .mcpDesc(description)
                        .isRequired(required ? 1 : 0)
                        .sortOrder(mappings.size() + 1)
                        .build();

                mappings.add(rootMapping);

                // 递归展开：会生成 name.a / name.a.b 等层级路径
                parseProperties(name, reqSchema.getJSONObject("properties"), reqSchema.getJSONArray("required"), definitions, mappings);
            } else {
                // 参数为标量：直接生成一条 mapping
                HTTPProtocolVO.ProtocolMapping mapping = HTTPProtocolVO.ProtocolMapping.builder()
                        .mappingType("request")
                        .parentPath(null)
                        .fieldName(name)
                        .mcpPath(name)
                        .mcpType(convertType(type))
                        .mcpDesc(description)
                        .isRequired(required ? 1 : 0)
                        .sortOrder(mappings.size() + 1)
                        .build();
                mappings.add(mapping);
            }
        }
    }
}
