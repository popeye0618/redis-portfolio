package com.example.realtimeranking.repository;

import com.example.realtimeranking.entity.GameScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GameScoreRepository extends JpaRepository<GameScore, Long> {
}
