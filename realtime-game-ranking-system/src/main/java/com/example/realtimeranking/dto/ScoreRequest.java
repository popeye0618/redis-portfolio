package com.example.realtimeranking.dto;

public record ScoreRequest(
        Long userId,
        String username,
        int score
) {
}
