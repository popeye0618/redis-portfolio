package com.example.redisserver1.entity;

import com.example.redisserver1.enums.CouponStatus;
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

    @Enumerated(EnumType.STRING)
    private CouponStatus status;

    private LocalDateTime issuedAt;
    private LocalDateTime expiredAt;

    @Builder
    public CouponIssuance(Long eventId, Long userId, LocalDateTime expiredAt) {
        this.eventId = eventId;
        this.userId = userId;
        this.status = CouponStatus.ACTIVE;
        this.issuedAt = LocalDateTime.now();
        this.expiredAt = expiredAt;
    }

    public void expire() {
        this.status = CouponStatus.EXPIRED;
    }
}
