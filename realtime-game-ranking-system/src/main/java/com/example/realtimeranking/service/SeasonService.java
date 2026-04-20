package com.example.realtimeranking.service;

import com.example.realtimeranking.dto.SeasonResponse;
import com.example.realtimeranking.entity.Season;
import com.example.realtimeranking.enums.SeasonStatus;
import com.example.realtimeranking.repository.SeasonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SeasonService {

    private final SeasonRepository seasonRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @Transactional
    public Season create(String name, LocalDateTime startAt, LocalDateTime endAt) {
        Season season = seasonRepository.save(
                Season.builder()
                        .name(name)
                        .startAt(startAt)
                        .endAt(endAt)
                        .build()
        );

        long ttlSeconds = Duration.between(LocalDateTime.now(), endAt).getSeconds();

        redisTemplate.opsForValue().set(
                "season:expire:" + season.getId(),
                String.valueOf(season.getId()),
                Duration.ofSeconds(ttlSeconds)
        );

        return season;
    }

    public Season getSeason(Long seasonId) {
        return seasonRepository.findById(seasonId)
                .orElseThrow(() -> new IllegalArgumentException("시즌이 없습니다."));
    }

    public List<SeasonResponse> getActiveSeasons() {
        return seasonRepository.findByStatus(SeasonStatus.ACTIVE)
                .stream()
                .map(SeasonResponse::from)
                .toList();
    }
}
