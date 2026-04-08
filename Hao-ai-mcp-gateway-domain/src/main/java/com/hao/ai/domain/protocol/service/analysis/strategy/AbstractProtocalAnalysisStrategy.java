package com.hao.ai.domain.protocol.service.analysis.strategy;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hao.ai.domain.protocol.model.valobj.http.HTTPProtocolVO;

import java.util.List;

public abstract class AbstractProtocalAnalysisStrategy implements IProtocalAnalysisStrategy{/**
 * 递归展开 OpenAPI schema.properties，生成扁平的 ProtocolMapping 列表。
 *
 * 这一步的目标是把树状结构（对象内嵌对象）拍平成：
 * - parentPath：父级路径（用于重建嵌套结构）
 * - mcpPath：完整路径（a.b.c），用于唯一定位字段
 *
 * 字段含义与库表 mcp_protocol_mapping 的 parent_path/mcp_path 一致。
 *
 * @param parentMcpPath 父级 MCP 路径（进入该方法时通常非空；根节点由具体策略创建，根节点 parentPath 为 null）
 * @param properties    当前对象的 properties 节点
 * @param requiredList  当前对象的 required 数组（用于判定字段是否必填）
 * @param definitions   components.schemas（用于展开 $ref 引用）
 * @param mappings      承载输出的映射列表
 */
protected void parseProperties(String parentMcpPath, JSONObject properties, JSONArray requiredList, JSONObject definitions, List<HTTPProtocolVO.ProtocolMapping> mappings) {
    if (properties == null) return;

    // sortOrder：仅保证“同一 parentPath 下”的字段顺序
    int sortOrder = 1;

    for (String propName : properties.keySet()) {
        JSONObject prop = properties.getJSONObject(propName);

        // 生成完整路径：a.b.c
        String currentMcpPath = parentMcpPath + "." + propName;

        // effectiveSchema：用于后续递归的实际 schema（若字段为 $ref，则需要先展开）
        JSONObject effectiveSchema = prop;
        String type = prop.getString("type");
        String description = prop.getString("description");

        // 字段为 $ref 时：从 definitions 中找到引用对象，再继续展开其 properties
        if (prop.containsKey("$ref")) {
            String ref = prop.getString("$ref");
            String refName = ref.substring(ref.lastIndexOf('/') + 1);
            effectiveSchema = definitions.getJSONObject(refName);

            // 字段未声明 type/description 时，用引用对象补齐
            if (type == null) type = effectiveSchema.getString("type");
            if (description == null) description = effectiveSchema.getString("description");
        }

        // 生成一条 mapping：用于后续生成 MCP 工具的 inputSchema/required/type/desc
        HTTPProtocolVO.ProtocolMapping mapping = HTTPProtocolVO.ProtocolMapping.builder()
                .mappingType("request")
                .parentPath(parentMcpPath)
                .fieldName(propName)
                .mcpPath(currentMcpPath)
                .mcpType(convertType(type))
                .mcpDesc(description)
                .isRequired(requiredList != null && requiredList.contains(propName) ? 1 : 0)
                .sortOrder(sortOrder++)
                .build();
        mappings.add(mapping);

        // 如果该字段仍是对象（或 $ref 展开的对象）并包含 properties，则继续递归
        if (effectiveSchema.containsKey("properties")) {
            parseProperties(currentMcpPath, effectiveSchema.getJSONObject("properties"), effectiveSchema.getJSONArray("required"), definitions, mappings);
        }
    }
}

    /**
     * OpenAPI schema.type -> MCP 类型（string/number/boolean/object/array）。
     * 目标是减少上游类型的多样性，让 MCP schema 更稳定。
     */
    protected String convertType(String type) {
        if (type == null) return "string";
        return switch (type.toLowerCase()) {
            case "string", "char", "date", "datetime" -> "string";
            case "integer", "int", "long", "double", "float", "number" -> "number";
            case "boolean", "bool" -> "boolean";
            case "array", "list" -> "array";
            default -> "object";
        };
    }

    /**
     * 将首字母变小写：例如 XxxRequest01 -> xxxRequest01。
     * 常用于 requestBody $ref 的根节点命名，让 MCP 根路径更贴近日常变量命名。
     */
    protected String toLowerCamel(String name) {
        if (name == null || name.isEmpty()) return name;
        char[] cs = name.toCharArray();
        cs[0] = java.lang.Character.toLowerCase(cs[0]);
        return new String(cs);
    }



}
