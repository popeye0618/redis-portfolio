# 실시간 게임 랭킹 시스템

> Redis Sorted Set을 활용한 실시간 랭킹 + Sliding Window Rate Limiting

## 목차

- [프로젝트 개요](#프로젝트-개요)
- [기술 스택](#기술-스택)
- [시스템 아키텍처](#시스템-아키텍처)
- [핵심 설계 — 왜 DB가 아닌 Redis인가](#핵심-설계--왜-db가-아닌-redis인가)
- [1주차 — 실시간 점수 등록 및 랭킹 조회](#1주차--실시간-점수-등록-및-랭킹-조회)
- [2주차 — Sliding Window Rate Limiting](#2주차--sliding-window-rate-limiting)
- [3주차 — 점수 범위 조회 및 구간별 랭킹](#3주차--점수-범위-조회-및-구간별-랭킹-예정)
- [4주차 — 시즌 종료 처리](#4주차--시즌-종료-처리-예정)
- [성능 비교](#성능-비교)
- [실행 방법](#실행-방법)
- [참고 자료](#참고-자료)

---

## 프로젝트 개요

수백만 사용자의 점수를 실시간으로 집계하고 순위를 조회하는 게임 랭킹 시스템입니다.

DB 기반 `ORDER BY` 쿼리의 한계를 직접 확인하고,
Redis Sorted Set의 O(log N) 특성으로 대용량 트래픽에서도
일정한 응답 시간을 보장하는 구조를 구현합니다.

```
테스트 환경: JMeter 동시 요청 20명 / Ramp-up 0초
```

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 4.x |
| ORM | Spring Data JPA |
| Database | MySQL 8.0 |
| Cache | Redis 8.0 |
| Redis Client | Redisson 4.0.0 |
| 부하 테스트 | Apache JMeter 5.6.3 |
| 인프라 | Docker Compose |

---

## 시스템 아키텍처

```
Client ──→ API Server (Spring Boot)
                  │
                  ├─→ Redis
                  │     ├─ leaderboard:season:{seasonId}       Sorted Set  전체 랭킹
                  │     └─ ratelimit:{userId}                  Sorted Set  슬라이딩 윈도우
                  │
                  └─→ MySQL
                        ├─ season          시즌 정보
                        └─ game_score      점수 이력 (비동기 저장)
```

---

## 핵심 설계 — 왜 DB가 아닌 Redis인가

```sql
-- MySQL로 실시간 랭킹을 구현하면
SELECT user_id, score
FROM game_scores
WHERE season_id = 1
ORDER BY score DESC
LIMIT 100;
-- 플레이어 100만 명 → 매 요청마다 인덱스 탐색
-- 초당 수천 번 호출 → DB 병목
```

```bash
# Redis Sorted Set은
ZREVRANGE leaderboard:season:1 0 99 WITHSCORES
# 항상 O(log N + M) → 플레이어 수에 무관하게 일정한 응답시간
```

재고의 원천은 Redis입니다.
`game_score` 테이블은 점수 이력 영구 저장 용도만 담당합니다.

---

## 1주차 — 실시간 점수 등록 및 랭킹 조회

### Redis Key 설계

```
leaderboard:season:{seasonId}
  score  │  member
─────────┼──────────────
  2300   │  userId:202   ← 1등
  1800   │  userId:303   ← 2등
  1500   │  userId:101   ← 3등
```

member에 `userId:{id}` 형태로 저장하여 추가 조회 없이 식별자를 바로 파싱합니다.

### 주요 명령어

| 기능 | 명령어 | 시간 복잡도 |
|------|--------|-----------|
| 점수 등록/갱신 | `ZADD` | O(log N) |
| 상위 N명 조회 | `ZREVRANGE` | O(log N + M) |
| 내 순위 조회 | `ZREVRANK` | O(log N) |
| 내 점수 조회 | `ZSCORE` | O(1) |
| 전체 참여자 수 | `ZCARD` | O(1) |

### 동작 확인

```bash
# 점수 등록
curl -X POST http://localhost:8080/api/ranking/seasons/1/score \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "username": "player1", "score": 1500}'

# 상위 10명 조회
curl http://localhost:8080/api/ranking/seasons/1/top

# 내 순위 조회
curl http://localhost:8080/api/ranking/seasons/1/me?userId=1

# Redis 직접 확인
redis-cli ZREVRANGE leaderboard:season:1 0 -1 WITHSCORES
```

---

## 2주차 — Sliding Window Rate Limiting

### 개념 — Fixed Window vs Sliding Window

```
고정 윈도우 (Fixed Window)
  00:00:58에 요청 100개 → 00:01:00 리셋 → 00:01:01에 다시 100개
  → 2초 사이에 200개 통과 가능. 경계 지점에서 뚫림

슬라이딩 윈도우 (Sliding Window)
  "지금 이 순간"으로부터 과거 N초를 항상 기준으로 삼음
  → 경계 관계없이 항상 정확하게 제한
```

### Race Condition 재현 — V1 (비원자적)

```
Thread A: ZCARD ratelimit:1 → 9 (한도 10, 통과 판단)
Thread B: ZCARD ratelimit:1 → 9 (한도 10, 통과 판단)
Thread A: ZADD ratelimit:1  → 10
Thread B: ZADD ratelimit:1  → 11  ← 한도 초과인데 통과됨
```

ZREMRANGEBYSCORE → ZCARD → ZADD 세 명령어가 분리 실행되어
명령어 사이에 다른 요청이 끼어들 수 있습니다.

**JMeter 테스트 결과 (한도 10, 동시 요청 20명)**

```bash
redis-cli ZCARD ratelimit:v1:1
# 결과: 18  (한도 10인데 18개 통과 → 8개 초과)
```

| 항목 | 결과 |
|------|------|
| 실제 통과 건수 | 18건 (한도 초과) |
| JMeter 오류율 | 10% (2건만 429) |
| Throughput | 571.4/sec |
| 정합성 | 실패 |

### 해결 — V2 (Lua Script 원자적 처리)

세 명령어를 Lua Script 하나로 묶어 원자성을 보장합니다.

```lua
-- rate_limit.lua
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)  -- 윈도우 밖 제거
local count = redis.call('ZCARD', key)                 -- 현재 요청 수 확인

if count >= limit then
    return 0  -- 한도 초과
end

redis.call('ZADD', key, now, requestId)  -- 요청 등록
redis.call('PEXPIRE', key, window)       -- TTL 갱신
return 1
```

**JMeter 테스트 결과 (동일 조건)**

```bash
redis-cli ZCARD ratelimit:v2:1
# 결과: 10  (정확)

redis-cli PTTL ratelimit:v2:1
# 결과: 42752  (TTL 정상 관리)
```

| 항목 | V1 (비원자적) | V2 (Lua Script) |
|------|-------------|----------------|
| 실제 통과 건수 | 18건 (초과) | 10건 (정확) |
| JMeter 오류율 | 10% | 50% (정상) |
| Throughput | 571.4/sec | 42.2/sec |
| 평균 응답시간 | 28ms | 453ms |
| 정합성 | 실패 | 성공 |

> V2 응답시간이 느린 이유: Lua Script 실행 비용이 아니라
> 한도에 걸린 10개 요청이 429 에러 처리 경로를 타기 때문입니다.
> 실제 통과된 10개 요청의 응답시간은 V1과 차이 없습니다.

---

## 3주차 — 점수 범위 조회 및 구간별 랭킹 (예정)

`ZRANGEBYSCORE`, `ZCOUNT`, `ZRANK` 활용한 구간 쿼리 구현 예정.

---

## 4주차 — 시즌 종료 처리 (예정)

TTL + Keyspace Notification으로 시즌 종료 시 랭킹 스냅샷 저장 및 Redis 키 정리 예정.

---

## 성능 비교

### Rate Limiting

| 버전 | 방식 | 정합성 | 통과 건수 | 오류율 |
|------|------|--------|---------|-------|
| V1 | 비원자적 (Java) | 실패 | 18건 (초과) | 10% |
| V2 | Lua Script | 성공 | 10건 (정확) | 50% |

---

## 실행 방법

```bash
# 1. 인프라 실행
docker-compose up -d

# 2. 애플리케이션 실행
./gradlew bootRun

# 3. 시즌 초기 데이터 생성
curl -X POST http://localhost:8080/api/seasons \
  -H "Content-Type: application/json" \
  -d '{"name": "시즌 1", "startAt": "2025-01-01T00:00:00", "endAt": "2025-03-31T23:59:59"}'

# 4. Rate Limiting 테스트 전 초기화
redis-cli DEL ratelimit:v1:1
redis-cli DEL ratelimit:v2:1

# 5. 랭킹 테스트 전 초기화
redis-cli DEL leaderboard:season:1
```

---

## 참고 자료

### Redis

- Redis Sorted Set 명령어: https://redis.io/docs/latest/commands/?group=sorted-set
- `ZADD` 레퍼런스: https://redis.io/docs/latest/commands/zadd/
- `ZREVRANGE` 레퍼런스: https://redis.io/docs/latest/commands/zrevrange/
- `ZREMRANGEBYSCORE` 레퍼런스: https://redis.io/docs/latest/commands/zremrangebyscore/
- `PEXPIRE` 레퍼런스: https://redis.io/docs/latest/commands/pexpire/
- Redis Rate Limiting 패턴: https://redis.io/glossary/rate-limiting/

### Spring

- Spring Data Redis `ZSetOperations`: https://docs.spring.io/spring-data/redis/docs/current/api/org/springframework/data/redis/core/ZSetOperations.html
- Spring `@Async` 공식 문서: https://docs.spring.io/spring-framework/reference/integration/scheduling.html#scheduling-annotation-support-async