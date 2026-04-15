package com.example.redisserver1.listener;

import com.example.redisserver1.entity.CouponIssuance;
import com.example.redisserver1.enums.CouponStatus;
import com.example.redisserver1.repository.CouponIssuanceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CouponExpirationListenerTest {

    @Autowired
    private CouponIssuanceRepository couponIssuanceRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Test
    @DisplayName("TTL 만료 시 쿠폰 상태가 EXPIRED로 변경된다")
    void expireCoupon() throws InterruptedException {
        // given: 쿠폰 발급 내역 저장
        CouponIssuance issuance = couponIssuanceRepository.save(
                CouponIssuance.builder()
                        .eventId(1L)
                        .userId(999L)
                        .expiredAt(LocalDateTime.now().plusSeconds(3))
                        .build()
        );

        redisTemplate.opsForValue().set(
                "coupon:expire:" + issuance.getId(),
                String.valueOf(issuance.getId()),
                Duration.ofSeconds(3)
        );

        // when: 4초 대기 (TTL 만료 + 이벤트 처리 시간 여유)
        Thread.sleep(4000);

        // then
        CouponIssuance expired = couponIssuanceRepository.findById(issuance.getId()).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(CouponStatus.EXPIRED);
    }

}