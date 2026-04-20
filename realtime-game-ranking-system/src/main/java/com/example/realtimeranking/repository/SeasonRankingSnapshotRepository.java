package com.example.realtimeranking.repository;

import com.example.realtimeranking.entity.SeasonRankingSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SeasonRankingSnapshotRepository extends JpaRepository<SeasonRankingSnapshot, Long> {
}
