package com.example.redisserver1.service;

import com.example.redisserver1.component.CouponLuaScript;
import com.example.redisserver1.entity.CouponIssuance;
import com.example.redisserver1.enums.IssuanceResult;
import com.example.redisserver1.repository.CouponIssuanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CouponServiceV3 {

    private final RedisTemplate<String, String> redisTemplate;
    private final CouponLuaScript couponLuaScript;
    private final CouponIssuanceRepository couponIssuanceRepository;

    public void issue(Long eventId, Long userId) {
        IssuanceResult result = couponLuaScript.issue(eventId, userId);

        switch (result) {
            case SUCCESS -> saveAsync(eventId, userId);
            case SOLD_OUT -> throw new IllegalStateException("재고가 소진되었습니다.");
            case DUPLICATE -> throw new IllegalStateException("이미 발급받은 쿠폰입니다.");
            default -> throw new IllegalStateException("처리 중 오류가 발생했습니다.");
        }
    }

    @Async
    @Transactional
    protected void saveAsync(Long eventId, Long userId) {
        LocalDateTime expiredAt = LocalDateTime.now().plusSeconds(10);

        CouponIssuance issuance = couponIssuanceRepository.save(
                CouponIssuance.builder()
                        .eventId(eventId)
                        .userId(userId)
                        .expiredAt(expiredAt)
                        .build()
        );

        redisTemplate.opsForValue().set(
                "coupon:expire:" + issuance.getId(),
                String.valueOf(issuance.getId()),
                Duration.ofSeconds(10)
        );
    }
}
