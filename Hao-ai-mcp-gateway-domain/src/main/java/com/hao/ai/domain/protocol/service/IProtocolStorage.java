package com.hao.ai.domain.protocol.service;

import com.hao.ai.domain.protocol.model.entity.StorageCommandEntity;
import java.util.List;

public interface IProtocolStorage {

    List<Long> doStorage(StorageCommandEntity commandEntity);

}
