package com.example.realtimeranking.dto;

import lombok.Builder;

@Builder
public record MyPercentileResponse(
        long rank,
        long totalParticipants,
        double topPercentile
) {
}
