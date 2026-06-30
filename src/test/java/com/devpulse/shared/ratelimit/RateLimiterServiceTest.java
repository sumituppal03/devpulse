package com.devpulse.shared.ratelimit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @Test
    void isAllowed_underLimit_returnsTrue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("ratelimit:standup:" + UUID.fromString("00000000-0000-0000-0000-000000000001")))
                .thenReturn(5L);

        RateLimiterService service = new RateLimiterService(redisTemplate);
        boolean allowed = service.isAllowed(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        assertThat(allowed).isTrue();
    }

    @Test
    void isAllowed_overLimit_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("ratelimit:standup:" + UUID.fromString("00000000-0000-0000-0000-000000000002")))
                .thenReturn(11L);

        RateLimiterService service = new RateLimiterService(redisTemplate);
        boolean allowed = service.isAllowed(UUID.fromString("00000000-0000-0000-0000-000000000002"));

        assertThat(allowed).isFalse();
    }
}