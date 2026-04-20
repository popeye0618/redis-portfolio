package com.example.rns.entity;

import com.example.rns.enums.NotificationStatus;
import com.example.rns.enums.NotificationType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    private String message;
    private String streamMessageId;

    @Enumerated(EnumType.STRING)
    private NotificationStatus status;

    private LocalDateTime createdAt;

    @Builder
    public Notification(Long userId, NotificationType type, String message, String streamMessageId) {
        this.userId = userId;
        this.type = type;
        this.message = message;
        this.streamMessageId = streamMessageId;
        this.status = NotificationStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public void markDelivered() {
        this.status = NotificationStatus.DELIVERED;
    }

    public void markFailed() {
        this.status = NotificationStatus.FAILED;
    }

}
