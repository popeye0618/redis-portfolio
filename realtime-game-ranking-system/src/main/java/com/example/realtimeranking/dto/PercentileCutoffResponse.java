package com.example.realtimeranking.dto;

import lombok.Builder;

@Builder
public record PercentileCutoffResponse(
        double percentile,
        double cutoffScore,
        long qualifiedCount
) {
}
