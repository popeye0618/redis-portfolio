package com.example.realtimeranking.dto;

import lombok.Builder;

@Builder
public record RankingResponse(
        int rank,
        Long userId,
        int score
) {
}
