package com.example.realtimeranking.repository;

import com.example.realtimeranking.entity.Season;
import com.example.realtimeranking.enums.SeasonStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeasonRepository extends JpaRepository<Season, Long> {

    List<Season> findByStatus(SeasonStatus status);

}
