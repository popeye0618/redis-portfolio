package com.example.realtimeranking.component;

import com.example.realtimeranking.entity.Season;
import com.example.realtimeranking.repository.SeasonRepository;
import com.example.realtimeranking.service.RankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Profile("!prod")
public class DataInitializer implements ApplicationRunner {

    private static final Long SEASON_ID = 1L;
    private final RankingService rankingService;
    private final SeasonRepository seasonRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        initSeason();
        initPlayers();
    }

    private void initSeason() {
        if (seasonRepository.existsById(SEASON_ID)) return;

        seasonRepository.save(Season.builder().name("시즌 1").startAt(LocalDateTime.now()).endAt(LocalDateTime.now().plusMonths(3)).build());
    }

    private void initPlayers() {
        // 서버 재시작 시 중복 등록 방지
        Boolean hasKey = redisTemplate.hasKey("leaderboard:season:" + SEASON_ID);
        if (Boolean.TRUE.equals(hasKey)) return;

        for (int i = 1; i <= 10; i++) {
            rankingService.recordScore(SEASON_ID, (long) i, "player" + i, i * 500);
        }

        System.out.println("[DataInitializer] 플레이어 10명 등록 완료");
    }
}
