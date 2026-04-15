package com.example.redisserver1.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponEvent {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private int totalQuantity;
    private int remainingQuantity;

    @Builder
    public CouponEvent(String name, int totalQuantity, int remainingQuantity) {
        this.name = name;
        this.totalQuantity = totalQuantity;
        this.remainingQuantity = remainingQuantity;
    }

    public void decrease() {
        if (this.remainingQuantity <= 0) {
            throw new IllegalStateException("재고가 없습니다!");
        }
        this.remainingQuantity--;
    }
}
