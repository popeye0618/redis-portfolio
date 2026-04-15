# 선착순 이벤트 쿠폰 발급 시스템

> Redis를 활용한 동시성 제어 — Race Condition 발생부터 Lua Script 해결까지

## 목차

- [프로젝트 개요](#프로젝트-개요)
- [기술 스택](#기술-스택)
- [시스템 아키텍처](#시스템-아키텍처)
- [핵심 문제 — Race Condition](#핵심-문제--race-condition)
- [V1 동기화 없는 버전](#v1-동기화-없는-버전)
- [V2 Redisson 분산 락](#v2-redisson-분산-락)
- [V3 Lua Script](#v3-lua-script)
- [V4 Sorted Set 대기열](#v4-sorted-set-대기열)
- [V5 TTL 기반 쿠폰 만료 처리](#v5-ttl-기반-쿠폰-만료-처리)
- [성능 비교](#성능-비교)
- [실행 방법](#실행-방법)

---

## 프로젝트 개요

선착순 쿠폰 발급처럼 **짧은 시간에 대량 트래픽이 몰리는 상황**에서
재고 정합성을 어떻게 지킬 수 있는지를 단계적으로 구현한 프로젝트입니다.

동기화 처리 없이 Race Condition을 의도적으로 재현하고,
Redisson 분산 락 → Redis Lua Script 순으로 개선하며
각 방식의 **정합성과 성능 트레이드오프**를 수치로 비교합니다.

```
테스트 환경: 재고 100개 / 동시 요청 200명 / JMeter Ramp-up 0초
```

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 3.x |
| ORM | Spring Data JPA |
| Database | MySQL 8.0 |
| Cache | Redis 7.2 |
| Redis Client | Redisson 3.27.2 |
| 부하 테스트 | Apache JMeter 5.6.3 |
| 인프라 | Docker Compose |

---

## 시스템 아키텍처

```
Client 1 ─┐
Client 2 ─┼─→ API Server (Spring Boot)
Client 3 ─┘         │
                     ├─→ Redis
                     │     ├─ coupon:stock:{eventId}    String  재고 수량 (V3)
                     │     ├─ coupon:issued:{eventId}   Set     발급자 목록 (V3)
                     │     ├─ coupon:lock:{eventId}     Lock    분산 락 (V2)
                     │     ├─ coupon:queue:{eventId}    Sorted Set  대기열 (V4)
                     │     └─ coupon:expire:{id}        String + TTL  만료 감지 (V5)
                     │
                     └─→ MySQL
                           ├─ coupon_event       이벤트 정보 / 재고 (V1, V2)
                           └─ coupon_issuance    발급 내역 (status: ACTIVE / USED / EXPIRED)
```

---

## 핵심 문제 — Race Condition

여러 쓰레드가 **조회 → 검증 → 차감**을 각자 실행할 때 문제가 발생합니다.

```
Thread A: remainingQuantity 조회 → 1
Thread B: remainingQuantity 조회 → 1   ← 같은 값을 읽음
Thread A: 1 > 0 통과 → decrease() → commit (remaining = 0)
Thread B: 1 > 0 통과 → decrease() → commit (remaining = -1)  ← 초과 발급
```

두 쓰레드가 동시에 "재고 있음"을 확인한 뒤 각자 차감하므로
총 발급 건수가 총 재고를 초과합니다. 이것이 **Lost Update** 패턴입니다.

---

## V1 동기화 없는 버전

### 구현

```java
@Transactional
public void issue(Long eventId, Long userId) {
    CouponEvent event = couponEventRepository.findById(eventId)
        .orElseThrow(() -> new IllegalArgumentException("이벤트가 없습니다."));

    // 문제 지점: 조회와 차감 사이에 다른 쓰레드가 끼어들 수 있음
    event.decrease();
    couponIssuanceRepository.save(
        CouponIssuance.builder()
            .eventId(eventId)
            .userId(userId)
            .build()
    );
}
```

### 테스트 결과

**JMeter 설정**: 200 threads / ramp-up 0초 / loop 1회

```sql
SELECT COUNT(*) FROM coupon_issuance WHERE event_id = 1;
-- 결과: 200  (재고 100개인데 200건 발급 → 100건 초과 발급)

SELECT remaining_quantity FROM coupon_event WHERE id = 1;
-- 결과: 80  (0이 되어야 하는데 80 → Lost Update 발생)
```

![version1.jpg](src/main/resources/images/version1.jpg)

### 원인 분석

`remaining_quantity = 80`의 의미:
- 200개 쓰레드 중 20개 트랜잭션만 실제로 차감이 반영됨
- 나머지 180개는 각자 읽은 값을 기준으로 덮어쓰기 → 차감 결과 유실
- 그러나 `couponIssuanceRepository.save()`는 200개 모두 실행됨

---

## V2 Redisson 분산 락

### 개념

`synchronized`는 단일 JVM 안에서만 동작합니다.
서버가 2대 이상이면 서버 간 동기화가 불가능합니다.
Redis 분산 락은 네트워크 너머 모든 서버가 공유하는 락이므로
서버 대수와 무관하게 동작합니다.

Redisson `RLock`은 내부적으로 Lua Script로 `SET NX PX` 명령어를 원자적으로
실행하여 락을 구현합니다.

### 구현

```java
public void issue(Long eventId, Long userId) {
    RLock lock = redissonClient.getLock("coupon:lock:" + eventId);
    try {
        // waitTime 3초: 3초 안에 락 못 얻으면 예외
        // leaseTime 2초: 서버 장애 시 자동 해제
        boolean acquired = lock.tryLock(3, 2, TimeUnit.SECONDS);
        if (!acquired) {
            throw new IllegalStateException("요청이 많습니다. 잠시 후 시도해주세요.");
        }
        processIssuance(eventId, userId);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    } finally {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}

@Transactional
protected void processIssuance(Long eventId, Long userId) {
    CouponEvent event = couponEventRepository.findById(eventId)
        .orElseThrow(() -> new IllegalArgumentException("이벤트가 없습니다."));
    event.decrease();
    couponIssuanceRepository.save(
        CouponIssuance.builder()
            .eventId(eventId)
            .userId(userId)
            .build()
    );
}
```

> `issue()`에서 락을 잡고 `processIssuance()`에서 트랜잭션을 시작한 이유:
> 트랜잭션 커밋 전에 락이 먼저 해제되면 다음 요청이 커밋 전 데이터를 읽을 수 있습니다.
> 락의 생명주기가 트랜잭션을 감싸야 합니다.

### 테스트 결과

**JMeter 설정**: 200 threads / ramp-up 0초 / loop 1회

```sql
SELECT COUNT(*) FROM coupon_issuance WHERE event_id = 1;
-- 결과: 100  (정확)

SELECT remaining_quantity FROM coupon_event WHERE id = 1;
-- 결과: 0  (정확)
```

![version2.jpg](src/main/resources/images/version2.jpg)

![V2.png](src/main/resources/images/V2.png)

### V2의 한계

평균 응답시간 **2,501ms**. 락 대기 쓰레드들이 블로킹되면서 발생합니다.

```
200명 동시 요청
→ 1명이 락 획득, 나머지 199명은 waitTime(3초) 동안 블로킹
→ 쓰레드 풀 점유 증가 → 응답 지연
→ 실제 서비스에서는 타임아웃 또는 사용자 이탈 발생
```

---

## V3 Lua Script

### 개념

락 없이 Redis 단에서 원자적으로 처리합니다.
Redis는 단일 쓰레드로 명령어를 순서대로 처리하므로,
Lua Script 실행 중에는 다른 명령어가 끼어들 수 없습니다.
경쟁 자체가 발생하지 않으므로 락이 필요 없습니다.

```
V2: 요청 → 락 획득 대기 → 락 획득 → DB 조회 → 차감 → 락 해제
V3: 요청 → Redis Lua Script 1번 호출 (끝)
```

V3에서 재고의 원천(Source of Truth)은 Redis입니다.
`coupon_event.remaining_quantity`는 V1, V2와의 호환을 위해 컬럼을 유지하며,
V3 흐름에서는 관여하지 않습니다.

### 구현

```lua
-- resources/scripts/coupon_issue.lua
local stock_key  = KEYS[1]   -- coupon:stock:{eventId}
local issued_key = KEYS[2]   -- coupon:issued:{eventId}
local user_id    = ARGV[1]

if redis.call('SISMEMBER', issued_key, user_id) == 1 then
    return -2  -- 중복 발급
end

local stock = tonumber(redis.call('GET', stock_key))
if stock == nil or stock <= 0 then
    return -1  -- 재고 소진
end

redis.call('DECR', stock_key)
redis.call('SADD', issued_key, user_id)
return 1
```

```java
@Component
@RequiredArgsConstructor
public class CouponLuaScript {

    private final RedisTemplate<String, String> redisTemplate;

    private final DefaultRedisScript<Long> script = new DefaultRedisScript<>() {{
        setScriptSource(
            new ResourceScriptSource(new ClassPathResource("scripts/coupon_issue.lua"))
        );
        setResultType(Long.class);
    }};

    public IssuanceResult issue(Long eventId, Long userId) {
        List<String> keys = List.of(
            "coupon:stock:"  + eventId,
            "coupon:issued:" + eventId
        );
        Long result = redisTemplate.execute(script, keys, String.valueOf(userId));
        return IssuanceResult.of(result);
    }
}
```

```java
@Service
@RequiredArgsConstructor
public class CouponServiceV3 {

    private final CouponLuaScript couponLuaScript;
    private final CouponIssuanceRepository couponIssuanceRepository;

    public void issue(Long eventId, Long userId) {
        IssuanceResult result = couponLuaScript.issue(eventId, userId);

        switch (result) {
            case SUCCESS   -> saveAsync(eventId, userId);
            case SOLD_OUT  -> throw new IllegalStateException("재고가 소진되었습니다.");
            case DUPLICATE -> throw new IllegalStateException("이미 발급받은 쿠폰입니다.");
            default        -> throw new IllegalStateException("처리 중 오류가 발생했습니다.");
        }
    }

    // Redis에서 정합성이 보장된 이후의 저장이므로 응답 경로에서 분리
    @Async
    @Transactional
    public void saveAsync(Long eventId, Long userId) {
        couponIssuanceRepository.save(
            CouponIssuance.builder()
                .eventId(eventId)
                .userId(userId)
                .build()
        );
    }
}
```

### 테스트 결과

**JMeter 설정**: 200 threads / ramp-up 0초 / loop 1회

```sql
SELECT COUNT(*) FROM coupon_issuance WHERE event_id = 1;
-- 결과: 100  (정확)
```

```bash
redis-cli GET coupon:stock:1    # "0"
redis-cli SCARD coupon:issued:1  # 100
```

![version3.jpg](src/main/resources/images/version3.jpg)

![V3.png](src/main/resources/images/V3.png)

---

## V4 Sorted Set 대기열

### 개념

V3는 빠르지만 극단적인 트래픽에서 Redis에도 부하가 집중됩니다.
대기열은 요청을 일단 줄 세우고 순서대로 처리하는 패턴입니다.

```
V3: 요청 → Lua Script 즉시 처리
V4: 요청 → ZADD 대기열 등록 → 순번 즉시 반환
                ↓
      스케줄러가 ZPOPMIN으로 순서대로 꺼내서 Lua Script 실행
```

score를 요청 시각(`System.currentTimeMillis()`)으로 사용하면
먼저 온 요청이 낮은 score를 가져 자동으로 선착순 정렬이 됩니다.

```
ZADD coupon:queue:1  1700000001000  "userId:101"   ← 1등
ZADD coupon:queue:1  1700000001001  "userId:202"   ← 2등
ZADD coupon:queue:1  1700000001002  "userId:303"   ← 3등
```

### 구현

```java
@Service
@RequiredArgsConstructor
public class CouponQueueService {

    private final RedisTemplate<String, String> redisTemplate;
    private final CouponLuaScript couponLuaScript;
    private final CouponIssuanceRepository couponIssuanceRepository;

    private static final String QUEUE_KEY = "coupon:queue:";

    public long enqueue(Long eventId, Long userId) {
        String key = QUEUE_KEY + eventId;
        String member = "userId:" + userId;

        // 이미 대기열에 있으면 현재 순번만 반환
        Long rank = redisTemplate.opsForZSet().rank(key, member);
        if (rank != null) {
            return rank + 1;
        }

        // score = 현재 시각 (선착순 보장)
        redisTemplate.opsForZSet().add(key, member, System.currentTimeMillis());
        rank = redisTemplate.opsForZSet().rank(key, member);
        return rank != null ? rank + 1 : -1;
    }

    public long getRank(Long eventId, Long userId) {
        Long rank = redisTemplate.opsForZSet()
            .rank(QUEUE_KEY + eventId, "userId:" + userId);
        return rank != null ? rank + 1 : -1;  // -1이면 처리 완료 또는 없음
    }

    @Scheduled(fixedDelay = 1000)
    public void processQueue() {
        Set<String> keys = redisTemplate.keys(QUEUE_KEY + "*");
        if (keys == null) return;

        for (String key : keys) {
            Long eventId = Long.parseLong(key.replace(QUEUE_KEY, ""));
            processBatch(eventId, key);
        }
    }

    private void processBatch(Long eventId, String key) {
        // 한 번에 10명씩 처리
        Set<String> members = redisTemplate.opsForZSet().range(key, 0, 9);
        if (members == null || members.isEmpty()) return;

        for (String member : members) {
            Long userId = Long.parseLong(member.replace("userId:", ""));
            IssuanceResult result = couponLuaScript.issue(eventId, userId);

            if (result == IssuanceResult.SUCCESS) {
                saveAsync(eventId, userId);
            }
            redisTemplate.opsForZSet().remove(key, member);
        }
    }

    @Async
    @Transactional
    public void saveAsync(Long eventId, Long userId) {
        couponIssuanceRepository.save(
            CouponIssuance.builder()
                .eventId(eventId)
                .userId(userId)
                .build()
        );
    }
}
```

### 테스트 결과

**1단계 — 대기열 등록**: JMeter 200 threads / ramp-up 0초

```bash
# 등록 직후 Redis 확인
redis-cli ZCARD coupon:queue:1      # 200 (전원 등록)
redis-cli ZRANGE coupon:queue:1 0 -1 WITHSCORES  # 순서 확인
```

**2단계 — 스케줄러 처리**: 10명/초 처리, 약 20초 후 완료

```sql
SELECT COUNT(*) FROM coupon_issuance WHERE event_id = 1;
-- 결과: 100  (정확)
```

```bash
redis-cli ZCARD coupon:queue:1  # 0 (대기열 소진)
```

---

## V5 TTL 기반 쿠폰 만료 처리

### 개념

발급된 쿠폰에 유효기간을 두고, 만료 시 자동으로 상태를 변경합니다.
Redis `Keyspace Notification`을 사용하면 TTL 만료 순간 이벤트를 수신할 수 있습니다.

```
쿠폰 발급 시: SET coupon:expire:{issuanceId} {issuanceId} EX 86400
                        ↓ 24시간 뒤 TTL 만료
Redis → "__keyevent@0__:expired" 채널에 키 이름 발행
                        ↓
CouponExpirationListener.onMessage() 호출
                        ↓
DB status: ACTIVE → EXPIRED
```

`coupon:expire:{id}` 키의 value는 의미 없습니다.
Redis 만료 이벤트는 키 이름만 전달하므로, 키 이름 자체에 `issuanceId`를 포함시켜
만료된 쿠폰을 식별합니다.

### Redis 설정

Keyspace Notification은 기본값이 비활성화입니다.

```yaml
# docker-compose.yml
redis:
  image: redis:8.0
  command: redis-server --notify-keyspace-events Ex
  ports:
    - "6380:6379"
```

### 구현

```java
@Component
public class CouponExpirationListener extends KeyExpirationEventMessageListener {

    private final CouponIssuanceRepository couponIssuanceRepository;

    // 부모 클래스가 super(listenerContainer) 호출을 요구하므로 생성자 직접 작성
    public CouponExpirationListener(
        RedisMessageListenerContainer listenerContainer,
        CouponIssuanceRepository couponIssuanceRepository
    ) {
        super(listenerContainer);
        this.couponIssuanceRepository = couponIssuanceRepository;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = message.toString();

        if (!expiredKey.startsWith("coupon:expire:")) return;

        Long issuanceId = Long.parseLong(expiredKey.replace("coupon:expire:", ""));

        couponIssuanceRepository.findById(issuanceId).ifPresent(issuance -> {
            issuance.expire();
            couponIssuanceRepository.save(issuance);
        });
    }
}
```

```java
@Configuration
@EnableAsync
@EnableScheduling
public class RedisConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
        RedisConnectionFactory connectionFactory
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }
}
```

### 테스트 결과

**수동 테스트**: TTL 5초로 설정 후 만료 이벤트 확인

```bash
# 터미널 1: TTL 실시간 감소 확인
watch -n 1 "redis-cli TTL coupon:expire:42"
# 5 → 4 → 3 → 2 → 1 → -2 (만료)
```

```sql
-- 만료 후 DB 상태 확인
SELECT id, status FROM coupon_issuance WHERE id = 42;
-- status: EXPIRED
```

**단위 테스트**: TTL 3초로 설정 후 4초 대기

```java
@Test
@DisplayName("TTL 만료 시 쿠폰 상태가 EXPIRED로 변경된다")
void expireCoupon() throws InterruptedException {
    CouponIssuance issuance = couponIssuanceRepository.save(/* ... */);
    redisTemplate.opsForValue().set(
        "coupon:expire:" + issuance.getId(),
        String.valueOf(issuance.getId()),
        Duration.ofSeconds(3)
    );

    Thread.sleep(4000);

    CouponIssuance expired = couponIssuanceRepository.findById(issuance.getId()).orElseThrow();
    assertThat(expired.getStatus()).isEqualTo(CouponStatus.EXPIRED);
}
```

---

## 성능 비교

| 버전 | 방식 | 정합성 | Throughput | 평균 응답시간 | V2 대비 |
|------|------|--------|-----------|--------------|---------|
| V1 | 동기화 없음 | 실패 | - | - | - |
| V2 | Redisson 분산 락 | 성공 | 58.4/sec | 2,501ms | 기준 |
| V3 | Redis Lua Script | 성공 | 212.8/sec | 762ms | TPS 3.6배 ↑ / 응답시간 70% ↓ |

### 개선 이유

V2는 락을 기다리는 쓰레드가 `waitTime` 동안 블로킹 상태로 살아있어 쓰레드 풀을 점유합니다.
V3는 Redis 안에서 Lua Script가 원자적으로 처리하므로 쓰레드 대기 자체가 없습니다.
DB 저장을 `@Async`로 응답 경로에서 분리하여 응답시간이 추가로 단축됩니다.

---

## 참고 자료

### Spring

- Spring Data Redis 공식 문서: https://docs.spring.io/spring-data/redis/reference/
- Spring `@Transactional` 동작 원리: https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative.html
- Spring `DefaultRedisScript` API: https://docs.spring.io/spring-data/redis/docs/current/api/org/springframework/data/redis/core/script/DefaultRedisScript.html
- Spring `@Async` 공식 문서: https://docs.spring.io/spring-framework/reference/integration/scheduling.html#scheduling-annotation-support-async
- Spring `@Scheduled` 공식 문서: https://docs.spring.io/spring-framework/reference/integration/scheduling.html#scheduling-annotation-support-scheduled
- Spring `KeyExpirationEventMessageListener`: https://docs.spring.io/spring-data/redis/docs/current/api/org/springframework/data/redis/listener/KeyExpirationEventMessageListener.html

### Redisson

- Redisson 공식 문서: https://redisson.org/docs/
- Redisson 분산 락 (RLock): https://redisson.org/docs/data-and-services/locks-and-synchronizers/

### Redis

- Redis Lua Scripting 공식 문서: https://redis.io/docs/latest/develop/interact/programmability/lua-api/
- `EVAL` 명령어 레퍼런스: https://redis.io/docs/latest/commands/eval/
- Redis Sorted Set 명령어: https://redis.io/docs/latest/commands/?group=sorted-set
- Redis Keyspace Notifications: https://redis.io/docs/latest/develop/use/keyspace-notifications/

### MySQL

- InnoDB Locking: https://dev.mysql.com/doc/refman/8.0/en/innodb-locking.html
