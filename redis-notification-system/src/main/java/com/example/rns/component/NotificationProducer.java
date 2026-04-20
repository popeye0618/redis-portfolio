package com.example.rns.component;

import com.example.rns.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class NotificationProducer {

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${notification.stream.key}")
    private String streamKey;

    public String publish(Long userId, NotificationType type, String message) {
        Map<String, String> fields = Map.of(
                "userId", String.valueOf(userId),
                "type", type.name(),
                "message", message
        );

        // XADD
        RecordId recordId = stringRedisTemplate.opsForStream().add(streamKey, fields);

        System.out.printf("[Producer] 발행 완료 | ID: %s | userId: %d | type: %s%n",
                recordId, userId, type);

        return recordId.getValue();
    }
}
