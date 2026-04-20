# 실시간 알림 · 이벤트 파이프라인

> Redis Stream + Pub/Sub + WebSocket — Kafka 없이 신뢰성 있는 이벤트 파이프라인 구축

## 목차

- [프로젝트 개요](#프로젝트-개요)
- [기술 스택](#기술-스택)
- [시스템 아키텍처](#시스템-아키텍처)
- [핵심 설계](#핵심-설계)
- [Version 1 — Redis Stream 기본 발행/소비](#version-1--redis-stream-기본-발행소비)
- [Version 2 — Consumer Group](#version-2--consumer-group)
- [Version 3 — Pub/Sub → WebSocket 브릿지](#version-3--pubsub--websocket-브릿지)
- [Version 4 — Pending Entry List 재처리](#version-4--pending-entry-list-재처리)
- [실행 방법](#실행-방법)
- [참고 자료](#참고-자료)

---

## 프로젝트 개요

별도의 Kafka 인프라 없이 Redis Stream만으로 신뢰성 있는 이벤트 파이프라인을 구현합니다.

단순 발행/소비에서 시작해 Consumer Group, WebSocket 실시간 알림, Pending Entry List 재처리까지 단계적으로 구현하며 각 단계가 왜 필요한지 문제를 먼저 정의하고 해결했습니다.

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 4.x |
| ORM | Spring Data JPA |
| Database | MySQL 8.0 |
| Cache | Redis 8.0 |
| 실시간 통신 | WebSocket (STOMP + SockJS) |
| 부하 테스트 | Apache JMeter 5.6.3 |
| 인프라 | Docker Compose |

---

## 시스템 아키텍처

```
Producer (API)
    │
    │ XADD
    ↓
notification:stream  (Redis Stream — append-only)
    │
    │ XREADGROUP
    ↓
Consumer (Spring @Scheduled)
    ├─→ MySQL           발행 내역 영구 저장 (status: DELIVERED)
    └─→ PUBLISH         notification:pubsub:{userId}
                            │
                            │ onMessage()
                            ↓
                    NotificationSubscriber
                            │
                            │ convertAndSend()
                            ↓
                    WebSocket (/topic/notifications/{userId})
                            │
                            ↓
                        Browser  실시간 알림 수신

[장애 복구]
XACK 미수신 메시지 → PEL 적재
PendingMessageProcessor (@Scheduled 30s)
    → XPENDING 감지 → XCLAIM → 재처리 → XACK
    → 3회 초과 시 Dead Letter 처리
```

---

## 핵심 설계

### Redis Stream vs Pub/Sub 역할 분리

```
Stream   → 메시지 내구성 보장. 소비자가 죽어도 메시지 유실 없음
Pub/Sub  → 실시간 브로드캐스트. 저장 없이 연결된 구독자에게 즉시 전달
```

두 가지를 조합하면 신뢰성(Stream)과 실시간성(Pub/Sub)을 동시에 확보합니다.

### Redis Stream vs Kafka

| 항목 | Redis Stream | Kafka |
|------|-------------|-------|
| 별도 인프라 | 불필요 | 필요 (ZooKeeper 등) |
| 메시지 보존 | 설정 가능 | 기본 영구 |
| 처리량 | 중간 규모 | 대규모 |
| 학습 비용 | 낮음 | 높음 |
| Consumer Group | 지원 | 지원 |
| PEL (재처리) | 지원 | Offset 기반 |

Redis Stream은 중간 규모 트래픽에서 Kafka의 복잡성 없이 동일한 신뢰성 패턴을 구현할 수 있습니다.

---

## Version 1 — Redis Stream 기본 발행/소비

### 개념

```
XADD notification:stream * userId 1 type COUPON message 발급완료
XREAD COUNT 10 STREAMS notification:stream 0
```

메시지 ID는 `타임스탬프-시퀀스` 형태로 자동 생성됩니다. append-only 구조라 한번 적재된 메시지는 수정되지 않습니다.

### 한계

```
문제 1: 여러 Consumer가 XREAD 하면 모두 같은 메시지를 읽음 → 중복 처리
문제 2: 처리 완료 여부를 추적하는 메커니즘이 없음 → 장애 시 재처리 불가
```

---

## Version 2 — Consumer Group

### 개념

```
XGROUP CREATE notification:stream notification-group 0

XREADGROUP GROUP notification-group consumer-1
  COUNT 10 STREAMS notification:stream >
  (> = 아직 아무도 안 가져간 새 메시지)
```

Consumer Group을 사용하면 같은 메시지를 두 Consumer가 동시에 받지 않습니다. 메시지를 읽는 순간 PEL에 기록되고, XACK를 보내면 제거됩니다.

### PEL (Pending Entry List)

```
XREADGROUP → 메시지 읽음 → PEL 기록: "consumer-1이 ID:xxx 가져감"
XACK       → 처리 완료  → PEL 제거

Consumer 장애 → XACK 미수신 → PEL에 영구 남음 → V4에서 재처리
```

### 동작 확인

```bash
# Group 정보 확인
redis-cli XINFO GROUPS notification:stream
# pending: 0, entries-read: 3, lag: 0

# PEL 확인 (처리 후 비어야 함)
redis-cli XPENDING notification:stream notification-group - + 10
# (empty array)
```

| 항목 | 결과 |
|------|------|
| DB status | DELIVERED |
| PEL | empty |
| Consumer Group | 정상 분산 처리 |

---

## Version 3 — Pub/Sub → WebSocket 브릿지

### 개념

```
Consumer 처리 완료
    │
    │ PUBLISH notification:pubsub:{userId} {message}
    ↓
Redis Pub/Sub
    │
    │ onMessage()
    ↓
NotificationSubscriber
    │
    │ messagingTemplate.convertAndSend("/topic/notifications/{userId}", message)
    ↓
WebSocket 연결된 브라우저에 실시간 전달
```

클라이언트는 `/topic/notifications/{userId}` 채널을 STOMP로 구독합니다. 알림 발행 시 해당 userId의 브라우저 화면에 즉시 메시지가 표시됩니다.

### 동작 확인

```bash
# Pub/Sub 채널 실시간 수신 확인
redis-cli SUBSCRIBE "notification:pubsub:1"
# 알림 발행 시 "WebSocket Test" 수신 확인
```

브라우저에서 WebSocket 연결 후 알림 발행 시 즉시 화면에 표시되는 것을 확인했습니다.

---

## Version 4 — Pending Entry List 재처리

### 개념

처리 실패로 XACK를 보내지 못한 메시지는 PEL에 남습니다. `PendingMessageProcessor`가 30초마다 PEL을 스캔하고 기준 시간(60초) 이상 된 메시지를 XCLAIM으로 재할당해 재처리합니다.

```
1차 재처리 실패 (전달횟수 2) → PEL 유지
2차 재처리 실패 (전달횟수 3) → Dead Letter 처리 → XACK → PEL 제거
```

### 동작 확인

```
[PEL] 미처리 메시지 1건 감지
[PEL] 재처리 대상 | ID: 1776656328136-0 | 경과: 30018ms | 전달횟수: 2
[PEL] 재처리 실패 | ID: 1776656328136-0 | 의도적 처리 실패
[PEL] 미처리 메시지 1건 감지
[PEL] 재처리 대상 | ID: 1776656328136-0 | 경과: 30008ms | 전달횟수: 3
[PEL] 재처리 실패 | ID: 1776656328136-0 | 의도적 처리 실패
[PEL] Dead Letter 처리 | ID: 1776656328136-0
```

```bash
# 최종 PEL 상태
redis-cli XPENDING notification:stream notification-group - + 10
# (empty array) ← Dead Letter XACK 후 PEL 완전히 비었음
```

| 항목 | 결과 |
|------|------|
| 재처리 감지 | 정상 (30초 주기) |
| XCLAIM 재할당 | 정상 |
| Dead Letter 처리 | 3회 초과 시 정상 처리 |
| 최종 PEL | empty |

---

## 실행 방법

```powershell
# 1. 인프라 실행
docker-compose up -d

# 2. 애플리케이션 실행
./gradlew bootRun

# 3. 알림 발행
Invoke-RestMethod -Method POST `
  -Uri "http://localhost:8080/api/notifications/publish" `
  -ContentType "application/json" `
  -Body '{"userId": 1, "type": "COUPON", "message": "쿠폰 발급 완료"}'

# 4. 유저 알림 조회
Invoke-RestMethod -Uri "http://localhost:8080/api/notifications/users/1"

# 5. WebSocket 실시간 테스트
# 브라우저에서 http://localhost:8080/index.html 열기

# 6. Stream 상태 확인
redis-cli XLEN notification:stream
redis-cli XRANGE notification:stream - +
redis-cli XINFO GROUPS notification:stream
redis-cli XPENDING notification:stream notification-group - + 10

# 7. PEL 재처리 테스트 (userId=99로 발행)
Invoke-RestMethod -Method POST `
  -Uri "http://localhost:8080/api/notifications/publish" `
  -ContentType "application/json" `
  -Body '{"userId": 99, "type": "SYSTEM", "message": "실패 테스트"}'
```

---

## 참고 자료

### Redis

- Redis Stream 공식 문서: https://redis.io/docs/latest/develop/data-types/streams/
- Redis Consumer Group: https://redis.io/docs/latest/develop/data-types/streams/#consumer-groups
- Redis Pub/Sub 공식 문서: https://redis.io/docs/latest/develop/interact/pubsub/
- `XADD` 레퍼런스: https://redis.io/docs/latest/commands/xadd/
- `XREAD` 레퍼런스: https://redis.io/docs/latest/commands/xread/
- `XREADGROUP` 레퍼런스: https://redis.io/docs/latest/commands/xreadgroup/
- `XACK` 레퍼런스: https://redis.io/docs/latest/commands/xack/
- `XPENDING` 레퍼런스: https://redis.io/docs/latest/commands/xpending/
- `XCLAIM` 레퍼런스: https://redis.io/docs/latest/commands/xclaim/
- `PUBLISH` 레퍼런스: https://redis.io/docs/latest/commands/publish/
- Redis Stream At-least-once: https://redis.io/docs/latest/develop/data-types/streams/#guarantees

### Spring

- Spring Data Redis Stream: https://docs.spring.io/spring-data/redis/reference/redis/redis-streams.html
- Spring WebSocket 공식 문서: https://docs.spring.io/spring-framework/reference/web/websocket.html
- Spring STOMP 가이드: https://docs.spring.io/spring-framework/reference/web/websocket/stomp.html
- Spring `@Scheduled`: https://docs.spring.io/spring-framework/reference/integration/scheduling.html#scheduling-annotation-support-scheduled
