package com.example.redisserver1.service;

import com.example.redisserver1.component.CouponLuaScript;
import com.example.redisserver1.entity.CouponIssuance;
import com.example.redisserver1.enums.IssuanceResult;
import com.example.redisserver1.repository.CouponIssuanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class CouponQueueService {

    private final RedisTemplate<String, String> redisTemplate;
    private final CouponLuaScript couponLuaScript;
    private final CouponIssuanceRepository couponIssuanceRepository;

    private static final String QUEUE_KEY = "coupon:queue";

    public long enqueue(Long eventId, Long userId) {
        String key = QUEUE_KEY + eventId;
        String member = "userId:" + userId;

        Long rank = redisTemplate.opsForZSet().rank(key, member);
        if (rank != null) {
            return rank + 1;
        }

        long score = System.currentTimeMillis();
        redisTemplate.opsForZSet().add(key, member, score);

        rank = redisTemplate.opsForZSet().rank(key, member);
        return rank != null ? rank + 1 : -1;
    }

    public long getRank(Long eventId, Long userId) {
        Long rank = redisTemplate.opsForZSet().rank(QUEUE_KEY + eventId, "userId:" + userId);

        return rank != null ? rank + 1 : -1;
    }

    @Scheduled(fixedDelay = 1000)
    public void processQueue() {
        Set<String> keys = redisTemplate.keys(QUEUE_KEY + "*");
        if (keys == null || keys.isEmpty()) return;

        for (String key : keys) {
            Long eventId = Long.parseLong(key.replace(QUEUE_KEY, ""));
            processBatch(eventId, key);
        }
    }

    private void processBatch(Long eventId, String key) {
        Set<String> members = redisTemplate.opsForZSet().range(key, 0, 9);
        if (members == null || members.isEmpty()) return;

        for (String member : members) {
            Long userId = Long.parseLong(member.replace("userId:", ""));
            IssuanceResult result = couponLuaScript.issue(eventId, userId);

            if (result == IssuanceResult.SUCCESS) {
                saveAsync(eventId, userId);
            }
            redisTemplate.opsForZSet().remove(key, member);
        }
    }

    @Async
    @Transactional
    public void saveAsync(Long eventId, Long userId) {
        couponIssuanceRepository.save(
            CouponIssuance.builder()
                    .eventId(eventId)
                    .userId(userId)
                    .build()
        );
    }
}
