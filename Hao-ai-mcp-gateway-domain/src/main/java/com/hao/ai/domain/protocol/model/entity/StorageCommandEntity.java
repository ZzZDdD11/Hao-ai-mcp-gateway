package com.hao.ai.domain.protocol.model.entity;

import com.hao.ai.domain.protocol.model.valobj.http.HTTPProtocolVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageCommandEntity {
    private String gatewayId;
    private List<HTTPProtocolVO> protocolVOList;
}
