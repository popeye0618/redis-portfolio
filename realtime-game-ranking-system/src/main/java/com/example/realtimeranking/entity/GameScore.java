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
public class GameScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long seasonId;
    private Long userId;
    private String username;
    private int score;
    private LocalDateTime recordedAt;

    @Builder
    public GameScore(Long seasonId, Long userId, String username, int score) {
        this.seasonId = seasonId;
        this.userId = userId;
        this.username = username;
        this.score = score;
        this.recordedAt = LocalDateTime.now();
    }
}
