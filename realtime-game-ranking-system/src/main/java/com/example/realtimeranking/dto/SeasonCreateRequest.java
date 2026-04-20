package com.example.realtimeranking.dto;

import java.time.LocalDateTime;

public record SeasonCreateRequest(
        String name,
        LocalDateTime startAt,
        LocalDateTime endAt
) {
}
