package com.example.realtimeranking.entity;

import com.example.realtimeranking.enums.SeasonStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Season {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private LocalDateTime startAt;
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    private SeasonStatus status;

    @Builder
    public Season(String name, LocalDateTime startAt, LocalDateTime endAt) {
        this.name = name;
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = SeasonStatus.ACTIVE;
    }

    public void end() {
        this.status = SeasonStatus.ENDED;
    }
}
