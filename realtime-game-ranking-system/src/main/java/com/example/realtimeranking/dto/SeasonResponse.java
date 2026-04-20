package com.example.realtimeranking.dto;

import com.example.realtimeranking.entity.Season;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record SeasonResponse(
        Long id,
        String name,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String status
) {
    public static SeasonResponse from(Season season) {
        return SeasonResponse.builder()
                .id(season.getId())
                .name(season.getName())
                .startAt(season.getStartAt())
                .endAt(season.getEndAt())
                .status(season.getStatus().name())
                .build();
    }
}
