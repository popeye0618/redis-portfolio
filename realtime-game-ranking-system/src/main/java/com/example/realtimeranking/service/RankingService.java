package com.example.realtimeranking.service;

import com.example.realtimeranking.dto.MyPercentileResponse;
import com.example.realtimeranking.dto.MyRankingResponse;
import com.example.realtimeranking.dto.PercentileCutoffResponse;
import com.example.realtimeranking.dto.RankingResponse;
import com.example.realtimeranking.entity.GameScore;
import com.example.realtimeranking.exception.RateLimitExceededException;
import com.example.realtimeranking.limiter.RateLimiterV1;
import com.example.realtimeranking.limiter.RateLimiterV2;
import com.example.realtimeranking.repository.GameScoreRepository;
import com.example.realtimeranking.repository.RankingRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RankingService {

    private final RankingRedisRepository rankingRedisRepository;
    private final GameScoreRepository gameScoreRepository;
    private final RateLimiterV2 rateLimiter;

    public void recordScore(Long seasonId, Long userId, String username, int score) {

        if (!rateLimiter.isAllowed(userId)) {
            throw new RateLimitExceededException("요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요.");
        }

        Double current = rankingRedisRepository.getMyScore(seasonId, userId);
        if (current != null && current >= score) {
            return;
        }

        rankingRedisRepository.saveScore(seasonId, userId, score);
        saveScoreAsync(seasonId, userId, username, score);
    }

    @Async
    @Transactional
    public void saveScoreAsync(Long seasonId, Long userId, String username, int score) {
        gameScoreRepository.save(
                GameScore.builder()
                        .seasonId(seasonId)
                        .userId(userId)
                        .username(username)
                        .score(score)
                        .build()
        );
    }

    public List<RankingResponse> getTopRankers(Long seasonId, int topN) {
        Set<ZSetOperations.TypedTuple<String>> result = rankingRedisRepository.getTopRankers(seasonId, topN);

        List<RankingResponse> list = new ArrayList<>();
        int rank = 1;
        for (ZSetOperations.TypedTuple<String> entry : result) {
            long userId = Long.parseLong(entry.getValue().replace("userId:", ""));
            list.add(RankingResponse.builder()
                    .rank(rank++)
                    .userId(userId)
                    .score(entry.getScore().intValue())
                    .build()
            );
        }

        return list;
    }

    public MyRankingResponse getMyRanking(Long seasonId, Long userId) {
        Long rank = rankingRedisRepository.getMyRank(seasonId, userId);
        Double score = rankingRedisRepository.getMyScore(seasonId, userId);
        Long total = rankingRedisRepository.getTotalCount(seasonId);

        if (rank == null) {
            throw new IllegalStateException("랭킹 정보가 없습니다.");
        }

        return MyRankingResponse.builder()
                .rank(rank)
                .score(score != null ? score.intValue() : 0)
                .totalParticipants(total)
                .build();
    }

    public MyPercentileResponse getMyPercentile(Long seasonId, Long userId) {
        Long myRank = rankingRedisRepository.getMyRank(seasonId, userId);
        Long total = rankingRedisRepository.getTotalCount(seasonId);

        if (myRank == null || total == null || total == 0) {
            throw new IllegalStateException("랭킹 정보가 없습니다.");
        }

        double percentile = Math.round((double) myRank / total * 1000) / 10.0;

        return MyPercentileResponse.builder()
                .rank(myRank)
                .totalParticipants(total)
                .topPercentile(percentile)
                .build();
    }

    public List<RankingResponse> getRankersByScoreRange(
            Long seasonId, double minScore, double maxScore
    ) {
        Set<ZSetOperations.TypedTuple<String>> result = rankingRedisRepository.getRankersByScoreRange(seasonId, minScore, maxScore);

        List<RankingResponse> list = new ArrayList<>();
        int rank = 1;
        for (ZSetOperations.TypedTuple<String> entry : result) {
            long userId = Long.parseLong(entry.getValue().replace("userId:", ""));
            list.add(RankingResponse.builder()
                    .rank(rank++)
                    .userId(userId)
                    .score(entry.getScore().intValue())
                    .build()
            );
        }
        return list;
    }

    public PercentileCutoffResponse getPercentileCutoff(Long seasonId, double percentile) {
        Double cutoffScore = rankingRedisRepository.getPercentileScore(seasonId, percentile);
        Long count = rankingRedisRepository.countByScoreRange(
                seasonId, cutoffScore != null ? cutoffScore : 0, Double.MAX_VALUE
        );

        return PercentileCutoffResponse.builder()
                .percentile(percentile)
                .cutoffScore(cutoffScore != null ? cutoffScore.intValue() : 0)
                .qualifiedCount(count)
                .build();
    }
}
