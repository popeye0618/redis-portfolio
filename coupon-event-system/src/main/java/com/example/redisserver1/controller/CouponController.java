package com.example.redisserver1.controller;

import com.example.redisserver1.service.CouponServiceV1;
import com.example.redisserver1.service.CouponServiceV2;
import com.example.redisserver1.service.CouponServiceV3;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class CouponController {

    private final CouponServiceV1 couponServiceV1;
    private final CouponServiceV2 couponServiceV2;
    private final CouponServiceV3 couponServiceV3;

    @PostMapping("/v1/coupons/{eventId}/issue")
    public ResponseEntity<String> issueV1(
            @PathVariable Long eventId,
            @RequestParam Long userId
    ) {
        couponServiceV1.issue(eventId, userId);
        return ResponseEntity.ok("발급 완료!");
    }

    @PostMapping("/v2/coupons/{eventId}/issue")
    public ResponseEntity<String> issueV2(
            @PathVariable Long eventId,
            @RequestParam Long userId
    ) {
        couponServiceV2.issue(eventId, userId);
        return ResponseEntity.ok("발급 완료!");
    }

    @PostMapping("/v3/coupons/{eventId}/issue")
    public ResponseEntity<String> issueV3(
            @PathVariable Long eventId,
            @RequestParam Long userId
    ) {
        couponServiceV3.issue(eventId, userId);
        return ResponseEntity.ok("발급 완료!");
    }
}
