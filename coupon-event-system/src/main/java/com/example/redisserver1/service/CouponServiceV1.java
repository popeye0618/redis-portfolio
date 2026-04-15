package com.example.redisserver1.service;

import com.example.redisserver1.entity.CouponEvent;
import com.example.redisserver1.entity.CouponIssuance;
import com.example.redisserver1.repository.CouponEventRepository;
import com.example.redisserver1.repository.CouponIssuanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CouponServiceV1 {

    private final CouponEventRepository couponEventRepository;
    private final CouponIssuanceRepository couponIssuanceRepository;

    @Transactional
    public void issue(Long eventId, Long userId) {
        CouponEvent event = couponEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("이벤트가 없습니다!"));

        event.decrease();
        couponIssuanceRepository.save(
                CouponIssuance.builder()
                        .eventId(eventId)
                        .userId(userId)
                        .build()
        );
    }
}
