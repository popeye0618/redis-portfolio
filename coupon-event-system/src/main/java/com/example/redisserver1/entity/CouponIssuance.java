package com.example.redisserver1.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponIssuance {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long eventId;
    private Long userId;
    private LocalDateTime issuedAt;

    @Builder
    public CouponIssuance(Long eventId, Long userId) {
        this.eventId = eventId;
        this.userId = userId;
        this.issuedAt = LocalDateTime.now();
    }
}
