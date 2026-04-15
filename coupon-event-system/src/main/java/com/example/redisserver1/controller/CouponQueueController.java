package com.example.redisserver1.controller;

import com.example.redisserver1.service.CouponQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v4/coupons")
public class CouponQueueController {

    private final CouponQueueService couponQueueService;

    @PostMapping("/{eventId}/queue")
    public ResponseEntity<Map<String, Object>> enqueue(
            @PathVariable Long eventId,
            @RequestParam Long userId
    ) {
        long rank = couponQueueService.enqueue(eventId, userId);
        return ResponseEntity.ok(Map.of(
                "message", "대기열에 등록되었습니다.",
                "rank", rank
        ));
    }

    @GetMapping("/{eventId}/queue/rank")
    public ResponseEntity<Map<String, Object>> getRank(
            @PathVariable Long eventId,
            @RequestParam Long userId
    ) {
        long rank = couponQueueService.getRank(eventId, userId);
        if (rank == -1) {
            return ResponseEntity.ok(Map.of("message", "처리 완료 또는 대기열에 없음"));
        }

        return ResponseEntity.ok(Map.of("rank", rank));
    }
}
