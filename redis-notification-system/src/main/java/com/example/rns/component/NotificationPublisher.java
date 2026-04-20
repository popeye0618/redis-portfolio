package com.example.rns.component;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationPublisher {

    private final StringRedisTemplate stringRedisTemplate;

    public void publish(Long userId, String message) {
        String channel = "notification:pubsub:" + userId;
        stringRedisTemplate.convertAndSend(channel, message);
        System.out.printf("[Publisher] Pub/Sub 발행 | channel: %s%n", channel);
    }
}
