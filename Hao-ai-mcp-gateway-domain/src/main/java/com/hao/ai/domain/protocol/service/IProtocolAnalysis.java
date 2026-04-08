package com.hao.ai.domain.protocol.service;

import com.hao.ai.domain.protocol.model.entity.AnalysisCommandEntity;
import com.hao.ai.domain.protocol.model.valobj.http.HTTPProtocolVO;

import java.util.List;

public interface IProtocolAnalysis {
    /**
     *
     * @param commandEntity
     * @return
     */
    List<HTTPProtocolVO> doAnalysis(AnalysisCommandEntity commandEntity);

}
