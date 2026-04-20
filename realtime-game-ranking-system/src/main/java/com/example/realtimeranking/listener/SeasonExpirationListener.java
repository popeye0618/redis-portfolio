package com.example.realtimeranking.listener;

import com.example.realtimeranking.entity.SeasonRankingSnapshot;
import com.example.realtimeranking.repository.RankingRedisRepository;
import com.example.realtimeranking.repository.SeasonRankingSnapshotRepository;
import com.example.realtimeranking.repository.SeasonRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class SeasonExpirationListener extends KeyExpirationEventMessageListener {

    private final SeasonRepository seasonRepository;
    private final RankingRedisRepository rankingRedisRepository;
    private final SeasonRankingSnapshotRepository snapshotRepository;
    private final RedisTemplate<String, String> redisTemplate;

    public SeasonExpirationListener(
            RedisMessageListenerContainer listenerContainer,
            SeasonRepository seasonRepository,
            RankingRedisRepository rankingRedisRepository,
            SeasonRankingSnapshotRepository snapshotRepository,
            RedisTemplate<String, String> redisTemplate
    ) {
        super(listenerContainer);
        this.seasonRepository = seasonRepository;
        this.rankingRedisRepository = rankingRedisRepository;
        this.snapshotRepository = snapshotRepository;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void onMessage(Message message, byte [] pattern) {
        String expiredKey = message.toString();
        if (!expiredKey.startsWith("season:expire:")) return;

        Long seasonId = Long.parseLong(expiredKey.replace("season:expire:", ""));

        saveSnapshot(seasonId);
        redisTemplate.delete("leaderboard:season:" + seasonId);

        seasonRepository.findById(seasonId).ifPresent(season -> {
            season.end();
            seasonRepository.save(season);
        });
    }

    private void saveSnapshot(Long seasonId) {
        Set<ZSetOperations.TypedTuple<String>> rankers = rankingRedisRepository.getTopRankers(seasonId, Integer.MAX_VALUE);

        if (rankers == null || rankers.isEmpty()) return;

        List<SeasonRankingSnapshot> snapshots = new ArrayList<>();
        int rank = 1;
        for (ZSetOperations.TypedTuple<String> entry : rankers) {
            long userId = Long.parseLong(entry.getValue().replace("userId:", ""));
            snapshots.add(
                    SeasonRankingSnapshot.builder()
                    .seasonId(seasonId)
                    .userId(userId)
                    .finalRank(rank++)
                    .finalScore(entry.getScore().intValue())
                    .build()
            );
        }

        snapshotRepository.saveAll(snapshots);
    }
}
