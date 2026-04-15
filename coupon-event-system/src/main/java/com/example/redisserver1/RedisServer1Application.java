package com.example.redisserver1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@SpringBootApplication
public class RedisServer1Application {

    public static void main(String[] args) {
        SpringApplication.run(RedisServer1Application.class, args);
    }

}
