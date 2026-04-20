package com.example.rns.controller;

import com.example.rns.component.NotificationProducer;
import com.example.rns.dto.NotificationRequest;
import com.example.rns.entity.Notification;
import com.example.rns.repository.NotificationRepository;
import lombok.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationProducer producer;
    private final NotificationRepository notificationRepository;

    @PostMapping("/publish")
    public ResponseEntity<Map<String, String>> publish(
            @RequestBody NotificationRequest request
    ) {
        String messageId = producer.publish(
                request.userId(),
                request.type(),
                request.message()
        );
        return ResponseEntity.ok(Map.of("messageId", messageId));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<List<Notification>> getByUser(
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(
                notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
        );
    }
}
