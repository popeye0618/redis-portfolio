package com.example.realtimeranking.controller;

import com.example.realtimeranking.dto.MyRankingResponse;
import com.example.realtimeranking.dto.RankingResponse;
import com.example.realtimeranking.dto.ScoreRequest;
import com.example.realtimeranking.service.RankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ranking")
public class RankingController {

    private final RankingService rankingService;

    @PostMapping("/seasons/{seasonId}/score")
    public ResponseEntity<Void> recordScore(
            @PathVariable Long seasonId,
            @RequestBody ScoreRequest request
    ) {
        rankingService.recordScore(seasonId, request.userId(), request.username(), request.score());

        return ResponseEntity.ok().build();
    }

    @GetMapping("/seasons/{seasonId}/top")
    public ResponseEntity<List<RankingResponse>> getTopRankers(
            @PathVariable Long seasonId,
            @RequestParam(defaultValue = "10") int topN
    ) {
        return ResponseEntity.ok(rankingService.getTopRankers(seasonId, topN));
    }

    @GetMapping("/seasons/{seasonId}/me")
    public ResponseEntity<MyRankingResponse> getMyRanking(
            @PathVariable Long seasonId,
            @RequestParam Long userId
    ) {
        return ResponseEntity.ok(rankingService.getMyRanking(seasonId, userId));
    }
}
