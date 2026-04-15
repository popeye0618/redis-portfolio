package com.example.redisserver1.repository;

import com.example.redisserver1.entity.CouponEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CouponEventRepository extends JpaRepository<CouponEvent, Long> {
}
