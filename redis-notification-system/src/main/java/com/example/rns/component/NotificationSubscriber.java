package com.example.rns.component;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class NotificationSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte @Nullable [] pattern) {
        try {
            String channel = new String(message.getChannel());
            String body = new String(message.getBody());

            String userId = channel.substring(channel.lastIndexOf(":") + 1);

            messagingTemplate.convertAndSend("/topic/notifications/" + userId, body);

            System.out.printf("[Subscriber] WebSocket 전달 | userId: %s | %s%n", userId, body);
        } catch (Exception e) {
            System.out.println("[Subscriber] 처리 실패: " + e.getMessage());
        }
    }
}
