package com.example.realtimeranking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class RealtimeGameRankingSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(RealtimeGameRankingSystemApplication.class, args);
    }

}
