package com.hao.ai.domain.protocol.service.analysis.strategy;

import com.alibaba.fastjson.JSONObject;
import com.hao.ai.domain.protocol.model.valobj.http.HTTPProtocolVO;

import java.util.List;

public interface IProtocalAnalysisStrategy {

    void doAnalysis(JSONObject operation, JSONObject definitions, List<HTTPProtocolVO.ProtocolMapping> mappings);

}
