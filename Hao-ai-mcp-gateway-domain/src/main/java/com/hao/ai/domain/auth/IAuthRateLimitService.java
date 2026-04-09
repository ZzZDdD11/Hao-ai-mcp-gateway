package com.hao.ai.domain.auth;

import com.hao.ai.domain.auth.model.entity.RateLimitCommandEntity;

public interface IAuthRateLimitService {

    boolean rateLimit(RateLimitCommandEntity rateLimitCommandEntity);
}
