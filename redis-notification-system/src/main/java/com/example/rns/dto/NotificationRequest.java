package com.example.rns.dto;

import com.example.rns.enums.NotificationType;

public record NotificationRequest(
        Long userId,
        NotificationType type,
        String message
) {
}
