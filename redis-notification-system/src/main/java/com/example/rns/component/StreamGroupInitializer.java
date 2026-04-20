package com.example.rns.component;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StreamGroupInitializer implements ApplicationRunner {

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${notification.stream.key}")
    private String streamKey;

    @Value("${notification.stream.group}")
    private String groupName;

    @Override
    public void run(ApplicationArguments args) {
        try {
            // XGROUP CREATE notification:stream notification-group 0
            stringRedisTemplate.opsForStream()
                    .createGroup(streamKey, ReadOffset.from("0"), groupName);
            System.out.println("[StreamGroup] 그룹 생성 완료: " + groupName);
        } catch (Exception e) {
            // BUSYGROUP: 이미 그룹이 존재하면 무시
            System.out.println("[StreamGroup] 그룹 이미 존재: " + groupName);
        }
    }
}
