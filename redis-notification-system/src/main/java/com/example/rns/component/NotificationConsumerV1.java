package com.example.rns.component;

import com.example.rns.entity.Notification;
import com.example.rns.enums.NotificationType;
import com.example.rns.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

//@Component
@RequiredArgsConstructor
public class NotificationConsumerV1 {

    private final StringRedisTemplate stringRedisTemplate;
    private final NotificationRepository notificationRepository;

    @Value("${notification.stream.key}")
    private String streamKey;

    private String lastReadId = "0";

    @Scheduled(fixedDelay = 1000)
    public void consume() {
        // XREAD COUNT 10 STREAMS notification:stream {lastReadId}
        List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                StreamReadOptions.empty().count(10),
                StreamOffset.create(streamKey, ReadOffset.from(lastReadId))
        );

        if (records == null || records.isEmpty()) return;

        for (MapRecord<String, Object, Object> record : records) {
            String messageId = record.getId().getValue();
            Map<Object, Object> fields = record.getValue();

            Long userId = Long.parseLong((String) fields.get("userId"));
            String type = (String) fields.get("type");
            String msg = (String) fields.get("message");

            Notification notification = notificationRepository.save(
                    Notification.builder()
                            .userId(userId)
                            .type(NotificationType.valueOf(type))
                            .message(msg)
                            .streamMessageId(messageId)
                            .build()
            );

            notification.markDelivered();
            notificationRepository.save(notification);

            System.out.printf("[ConsumerV1] 처리 완료 | ID: %s | userId: %d%n", messageId, userId);

            lastReadId = messageId;
        }
    }
}
