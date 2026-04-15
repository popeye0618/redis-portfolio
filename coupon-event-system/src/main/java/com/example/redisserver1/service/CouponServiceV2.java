package com.example.redisserver1.service;

import com.example.redisserver1.entity.CouponEvent;
import com.example.redisserver1.entity.CouponIssuance;
import com.example.redisserver1.repository.CouponEventRepository;
import com.example.redisserver1.repository.CouponIssuanceRepository;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class CouponServiceV2 {

    private final RedissonClient redissonClient;
    private final CouponEventRepository couponEventRepository;
    private final CouponIssuanceRepository couponIssuanceRepository;


    public void issue(Long eventId, Long userId) {
        String lockKey = "coupon:lock:" + eventId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            int waitTime = 3;
            int leaseTime = 2;
            boolean acquired = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);

            if (!acquired) {
                throw new IllegalStateException("요청이 많습니다. 잠시 후에 다시 시도해주세요.");
            }
            processIssuance(eventId, userId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("락 획득 중 인터럽트 발생");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Transactional
    protected void processIssuance(Long eventId, Long userId) {
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
