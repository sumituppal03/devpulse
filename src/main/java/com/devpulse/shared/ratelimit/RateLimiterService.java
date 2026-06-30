package com.devpulse.shared.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class RateLimiterService {

    private static final int MAX_REQUESTS_PER_WINDOW = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate redisTemplate;

    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isAllowed(UUID tenantId) {
        String key = "ratelimit:standup:" + tenantId;

        Long currentCount = redisTemplate.opsForValue().increment(key);

        if (currentCount != null && currentCount == 1L) {
            redisTemplate.expire(key, WINDOW);
        }

        return currentCount != null && currentCount <= MAX_REQUESTS_PER_WINDOW;
    }
}