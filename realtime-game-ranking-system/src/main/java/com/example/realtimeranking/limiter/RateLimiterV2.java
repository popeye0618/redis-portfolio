package com.example.realtimeranking.limiter;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RateLimiterV2 {

    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<Long> script = new DefaultRedisScript<>() {{
        setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/rate_limit.lua")));
        setResultType(Long.class);
    }};

    private static final long WINDOW_MS = 60_000L;
    private static final int LIMIT = 10;

    public boolean isAllowed(Long userId) {
        String key = "rate_limit:v2:" + userId;
        long now = System.currentTimeMillis();
        String requestId = now + ":" + Math.random();

        Long result = redisTemplate.execute(
                script,
                List.of(key),
                String.valueOf(now),
                String.valueOf(WINDOW_MS),
                String.valueOf(LIMIT),
                requestId
        );

        return Long.valueOf(1).equals(result);
    }
}
