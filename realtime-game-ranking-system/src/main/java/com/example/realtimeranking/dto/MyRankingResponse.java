package com.example.realtimeranking.dto;

import lombok.Builder;

@Builder
public record MyRankingResponse(
        Long rank,
        int score,
        Long totalParticipants
) {
}
