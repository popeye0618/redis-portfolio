package com.example.redisserver1.component;

import com.example.redisserver1.enums.IssuanceResult;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CouponLuaScript {

    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<Long> script = new DefaultRedisScript<>() {{
        setScriptSource(
                new ResourceScriptSource(new ClassPathResource("scripts/coupon_issue.lua"))
        );
        setResultType(Long.class);
    }};

    public IssuanceResult issue(Long eventId, Long userId) {
        List<String> keys = List.of(
                "coupon:stock:" + eventId,
                "coupon:issued:" + eventId
        );
        Long result = redisTemplate.execute(script, keys, String.valueOf(userId));

        return IssuanceResult.of(result);
    }

}
