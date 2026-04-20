package com.example.realtimeranking.controller;

import com.example.realtimeranking.dto.SeasonCreateRequest;
import com.example.realtimeranking.dto.SeasonResponse;
import com.example.realtimeranking.entity.Season;
import com.example.realtimeranking.service.SeasonService;
import lombok.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seasons")
public class SeasonController {

    private final SeasonService seasonService;

    @PostMapping
    public ResponseEntity<SeasonResponse> create(@RequestBody SeasonCreateRequest request) {
        Season season = seasonService.create(
                request.name(),
                request.startAt(),
                request.endAt()
        );
        return ResponseEntity.ok(SeasonResponse.from(season));
    }

    @GetMapping("/{seasonId}")
    public ResponseEntity<SeasonResponse> getSeason(@PathVariable Long seasonId) {
        Season season = seasonService.getSeason(seasonId);
        return ResponseEntity.ok(SeasonResponse.from(season));
    }

    @GetMapping
    public ResponseEntity<List<SeasonResponse>> getActiveSeasons() {
        return ResponseEntity.ok(seasonService.getActiveSeasons());
    }

}
