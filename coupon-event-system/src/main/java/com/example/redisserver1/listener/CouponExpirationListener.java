package com.example.redisserver1.listener;

import com.example.redisserver1.repository.CouponIssuanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CouponExpirationListener extends KeyExpirationEventMessageListener {

    private final CouponIssuanceRepository couponIssuanceRepository;

    public CouponExpirationListener(RedisMessageListenerContainer listenerContainer, CouponIssuanceRepository couponIssuanceRepository) {
        super(listenerContainer);
        this.couponIssuanceRepository = couponIssuanceRepository;
    }

    @Override
    public void onMessage(Message message, byte [] pattern) {

        log.info("TTL 자동만료 리스너 동작");

        String expiredKey = message.toString();

        if (!expiredKey.startsWith("coupon:expire:")) return;

        Long issuanceId = Long.parseLong(expiredKey.replace("coupon:expire:", ""));

        couponIssuanceRepository.findById(issuanceId)
                .ifPresent(issuance -> {
                    issuance.expire();
                    couponIssuanceRepository.save(issuance);
                });
    }
}
