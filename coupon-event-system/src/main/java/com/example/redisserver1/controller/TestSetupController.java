package com.example.redisserver1.controller;

import com.example.redisserver1.entity.CouponEvent;
import com.example.redisserver1.repository.CouponEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/test")
@Profile("!prod")
public class TestSetupController {

    private final RedisTemplate<String, String> redisTemplate;
    private final CouponEventRepository couponEventRepository;

    @GetMapping("/reset/{eventId}")
    public ResponseEntity<String> reset(@PathVariable Long eventId) {
        CouponEvent event = couponEventRepository.findById(eventId)
                .orElseThrow();


        redisTemplate.opsForValue().set(
                "coupon:stock:" + eventId,
                String.valueOf(event.getTotalQuantity())
        );

        redisTemplate.delete("coupon:issued:" + eventId);

        return ResponseEntity.ok("초기화 완료. 재고: " + event.getTotalQuantity());
    }
}
