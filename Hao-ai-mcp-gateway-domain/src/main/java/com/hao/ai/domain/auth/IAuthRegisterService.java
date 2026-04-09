package com.hao.ai.domain.auth;

import com.hao.ai.domain.auth.model.entity.RegisterCommandEntity;

public interface IAuthRegisterService {
    String register(RegisterCommandEntity commandEntity);
}
