package com.example.realtimeranking.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class RankingRedisRepository {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String KEY_PREFIX = "leaderboard:season:";

    private String key(Long seasonId) {
        return KEY_PREFIX + seasonId;
    }

    public void saveScore(Long seasonId, Long userId, double score) {
        redisTemplate.opsForZSet().add(key(seasonId), "userId:" + userId, score);
    }

    public Set<ZSetOperations.TypedTuple<String>> getTopRankers(Long seasonId, int topN) {
        return redisTemplate.opsForZSet()
                .reverseRangeWithScores(key(seasonId), 0, topN - 1);
    }

    public Long getMyRank(Long seasonId, Long userId) {
        Long rank = redisTemplate.opsForZSet()
                .reverseRank(key(seasonId), "userId:" + userId);
        return rank != null ? rank + 1 : null;
    }

    public Double getMyScore(Long seasonId, Long userId) {
        return redisTemplate.opsForZSet()
                .score(key(seasonId), "userId:" + userId);
    }

    public Long getTotalCount(Long seasonId) {
        return redisTemplate.opsForZSet().zCard(key(seasonId));
    }
}
