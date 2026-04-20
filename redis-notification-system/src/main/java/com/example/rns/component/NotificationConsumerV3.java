package com.example.rns.component;

import com.example.rns.entity.Notification;
import com.example.rns.enums.NotificationType;
import com.example.rns.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class NotificationConsumerV3 {

    private final StringRedisTemplate stringRedisTemplate;
    private final NotificationRepository notificationRepository;
    private final NotificationPublisher publisher;

    @Value("${notification.stream.key}")
    private String streamKey;

    @Value("${notification.stream.group}")
    private String groupName;

    @Value("${notification.stream.consumer}")
    private String consumerName;

    @Scheduled(fixedDelay = 1000)
    public void consume() {
        List<@NonNull MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                Consumer.from(groupName, consumerName),
                StreamReadOptions.empty().count(10),
                StreamOffset.create(streamKey, ReadOffset.lastConsumed())
        );

        if (records == null || records.isEmpty()) return;

        for (MapRecord<String, Object, Object> record : records) {
            String messageId = record.getId().getValue();

            try {
                process(record);

                // 처리 성공 → XACK으로 PEL에서 제거
                stringRedisTemplate.opsForStream()
                        .acknowledge(streamKey, groupName, record.getId());

                System.out.printf("[Consumer] XACK 완료 | ID: %s%n", messageId);
            } catch (Exception e) {
                // 처리 실패 → XACK 안 함 → PEL에 남음
                System.out.printf("[Consumer] 처리 실패 | ID: %s | %s%n",
                        messageId, e.getMessage());
            }
        }
    }

    private void process(MapRecord<String, Object, Object> record) {
        Map<Object, Object> fields = record.getValue();
        String messageId = record.getId().getValue();

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

        publisher.publish(userId, msg);

        System.out.printf("[ConsumerV3] 처리 완료 | ID: %s | userId: %d%n", messageId, userId);
    }
}
