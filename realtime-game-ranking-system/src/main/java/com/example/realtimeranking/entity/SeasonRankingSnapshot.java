package com.example.realtimeranking.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeasonRankingSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long seasonId;
    private Long userId;
    private int finalRank;
    private int finalScore;
    private LocalDateTime savedAt;

    @Builder
    public SeasonRankingSnapshot(Long seasonId, Long userId, int finalRank, int finalScore) {
        this.seasonId = seasonId;
        this.userId = userId;
        this.finalRank = finalRank;
        this.finalScore = finalScore;
        this.savedAt = LocalDateTime.now();
    }
}
