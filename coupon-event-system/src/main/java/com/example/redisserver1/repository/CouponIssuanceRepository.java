package com.example.redisserver1.repository;

import com.example.redisserver1.entity.CouponIssuance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CouponIssuanceRepository extends JpaRepository<CouponIssuance, Long> {
}
