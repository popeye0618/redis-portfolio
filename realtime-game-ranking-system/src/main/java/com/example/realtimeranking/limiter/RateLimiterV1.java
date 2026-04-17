package com.example.realtimeranking.limiter;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RateLimiterV1 {

    private final RedisTemplate<String, String> redisTemplate;

    private static final long WINDOW_MS = 60_000L;
    private static final int LIMIT = 10;

    public boolean isAllowed(Long userId) {
        String key = "rate_limit:v1:" + userId;
        long now = System.currentTimeMillis();

        redisTemplate.opsForZSet()
                .removeRangeByScore(key, 0, now - WINDOW_MS);

        Long count = redisTemplate.opsForZSet().zCard(key);

        if (count != null && count >= LIMIT) {
            return false;
        }

        redisTemplate.opsForZSet()
                .add(key, now + ":" + Math.random(), now);
        redisTemplate.expire(key, Duration.ofMillis(WINDOW_MS));

        return true;
    }
}
