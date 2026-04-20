package com.example.rns.component;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PendingMessageProcessor {

    private final StringRedisTemplate stringRedisTemplate;
    private final NotificationConsumerV4 consumer;

    @Value("${notification.stream.key}")
    private String streamKey;

    @Value("${notification.stream.group}")
    private String groupName;

    @Value("${notification.stream.consumer}")
    private String consumerName;

    private static final long PENDING_THRESHOLD_MS = 5_000L;

    @Scheduled(fixedDelay = 30_000)
    public void processPendingMessages() {
        PendingMessagesSummary summary = stringRedisTemplate.opsForStream().pending(streamKey, groupName);

        if (summary == null || summary.getTotalPendingMessages() == 0) {
            return;
        }

        System.out.printf("[PEL] 미처리 메시지 %d건 감지%n", summary.getTotalPendingMessages());

        PendingMessages pending = stringRedisTemplate.opsForStream()
                .pending(streamKey, groupName, Range.unbounded(), 100L);

        for (PendingMessage message : pending) {
            long elapsedMs = message.getElapsedTimeSinceLastDelivery().toMillis();

            if (elapsedMs < PENDING_THRESHOLD_MS) continue;

            System.out.printf("[PEL] 재처리 대상 | ID: %s | 경과: %dms | 전달횟수: %d%n",
                    message.getIdAsString(),
                    elapsedMs,
                    message.getTotalDeliveryCount());

            List<@NonNull MapRecord<String, Object, Object>> claimed = stringRedisTemplate.opsForStream().claim(
                    streamKey,
                    groupName,
                    consumerName,
                    Duration.ofMillis(PENDING_THRESHOLD_MS),
                    message.getId()
            );

            if (claimed == null || claimed.isEmpty()) continue;

            for (MapRecord<String, Object, Object> record : claimed) {
                try {
                    consumer.process(record);

                    stringRedisTemplate.opsForStream()
                            .acknowledge(streamKey, groupName, record.getId());

                    System.out.printf("[PEL] 재처리 성공 | ID: %s%n",
                            record.getId().getValue());
                } catch (Exception e) {
                    System.out.printf("[PEL] 재처리 실패 | ID: %s | %s%n",
                            record.getId().getValue(), e.getMessage());

                    // 전달 횟수가 3회 초과면 Dead Letter로 처리
                    if (message.getTotalDeliveryCount() >= 3) {
                        handleDeadLetter(record);
                    }
                }
            }

        }
    }

    private void handleDeadLetter(MapRecord<String, Object, Object> record) {
        System.out.printf("[PEL] Dead Letter 처리 | ID: %s%n", record.getId().getValue());

        // 더 이상 재처리하지 않도록 XACK 후 별도 로그 또는 알림
        stringRedisTemplate.opsForStream().acknowledge(streamKey, groupName, record.getId());

        // 실제 서비스에서는 Dead Letter Queue로 이동하거나 알림을 보냄
    }
}
