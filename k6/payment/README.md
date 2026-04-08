# k6 Circuit Breaker 테스트

Commerce API의 PG 결제 호출에 적용된 Resilience4j CircuitBreaker의 동작을
부하 환경에서 검증한다.

---

## 현재 CB 설정

| 항목 | 값 |
|------|----|
| `slidingWindowSize` | 10 |
| `failureRateThreshold` | 50% |
| `waitDurationInOpenState` | 30s |
| `permittedNumberOfCallsInHalfOpenState` | 3 |
| RestTemplate readTimeout | 3s |

**PG Simulator 동작 (포트 8082):**
- 모든 요청에 100~500ms 랜덤 지연
- 40% 확률로 500 INTERNAL_ERROR 반환
- 나머지 60%: SUCCESS 70% / LIMIT_EXCEEDED 20% / INVALID_CARD 10%

→ 자연 실패율 ~40%, `slidingWindowSize=10` 기준 통계적으로 CB가 트리핑됨.

---

## 디렉터리 구조

```
k6/
├── utils/
│   ├── client.js        # baseURL, 공통 헤더, CB 상태 polling 헬퍼
│   └── setup.js         # 유저 생성, 상품 조회, 주문 N개 생성
├── scenarios/
│   ├── 01-baseline.js   # 정상 동작 확인 (낮은 RPS)
│   ├── 02-trip.js       # CB OPEN 유도 (burst 동시 요청)
│   └── 03-recovery.js   # OPEN → HALF_OPEN → CLOSED 전 사이클 관찰
└── README.md
```

---

## 사전 요구사항

```bash
# k6 설치
brew install k6

# 인프라 (MySQL, Redis) 실행
docker compose -f docker/infra-compose.yml up -d

# PG Simulator 실행 (포트 8082)
./gradlew :apps:pg-simulator:bootRun

# Commerce API 실행 (포트 8080, Actuator 포트 8081)
./gradlew :apps:commerce-api:bootRun

# 상품 데이터 시드 (최소 1개 이상 필요)
# Swagger UI: http://localhost:8080/swagger-ui.html 에서 브랜드 → 상품 생성
```

---

## 실행 방법

```bash
# 01: 정상 동작 확인 (CB CLOSED, PG 실패 처리 검증)
k6 run k6/scenarios/01-baseline.js

# 02: CB OPEN 유도 (20 VU burst)
k6 run k6/scenarios/02-trip.js

# 03: 전 사이클 관찰 (OPEN → HALF_OPEN → CLOSED, ~90s)
k6 run k6/scenarios/03-recovery.js

# 05: 주문 10,000건 처리시간 측정 (queue/token 플로우 포함)
TOTAL_ITERATIONS=10000 VUS=50 USER_POOL_SIZE=200 \
  k6 run k6/payment/scenarios/05-batch-size-measurement.js
```

---

## 배치 크기 산정용 측정 시나리오

`05-batch-size-measurement.js` 는 다음 목적을 가진다.

- 총 10,000건 주문 요청 수행
- queue enter / position / token 획득 플로우는 그대로 사용
- 실제 **주문 API 호출 자체의 duration** 만 `order_only_duration` metric 으로 별도 기록
- `avg`, `p(95)`, `p(99)`, `p(99.9)` 를 확인해 배치 크기 산정 근거로 활용

### 주요 파라미터

- `TOTAL_ITERATIONS` : 총 주문 건수 (기본 10000)
- `VUS` : 동시 사용자 수 (기본 50)
- `USER_POOL_SIZE` : 재사용할 유저 풀 크기 (기본 200)
- `QUEUE_POLL_TIMEOUT_SECONDS` : queue token 대기 최대 시간 (기본 180초)
- `MAX_DURATION` : 전체 시나리오 최대 허용 시간 (기본 60분)

### 해석 기준

- `avg` : 전체 주문 요청의 평균 처리시간
- `p(95)` : 전체 요청 중 95%가 이 시간 이하
- `p(99)` : 전체 요청 중 99%가 이 시간 이하
- `p(99.9)` : 전체 요청 중 99.9%가 이 시간 이하

권장 batch size 산정은 보통:

- `p(95)` 를 주 기준
- `p(99)` / `p(99.9)` 를 tail latency 참고값

으로 활용한다.

### 사전 준비

- 상품 재고는 `TOTAL_ITERATIONS` 이상 필요
- queue admission batch-size / fixed-delay 가 너무 작으면 전체 측정 시간이 길어질 수 있음

---

## CB 상태 실시간 모니터링

별도 터미널에서:

```bash
# CB 현재 상태
watch -n 1 "curl -s http://localhost:8081/actuator/circuitbreakers | jq '.circuitBreakers[\"pg-payment\"].state'"

# CB 이벤트 스트림 (OPEN/HALF_OPEN/CLOSED 전환 기록)
curl -s http://localhost:8081/actuator/circuitbreakerevents/pg-payment | jq '.circuitBreakerEvents[-10:]'
```

---

## 시나리오별 검증 기준

### 01-baseline
- HTTP 200: PG SUCCESS (60% 중 SUCCESS 70% = ~42%)
- HTTP 400/500 (PG 실패 body): PG LIMIT_EXCEEDED / INVALID_CARD / INTERNAL_ERROR
- HTTP 500 (즉시 fallback 메시지): CB OPEN → baseline에서는 드물어야 함 (`cb_open_rate < 10%`)

### 02-trip
| 지표 | 기대값 |
|------|--------|
| `cb_open_fallbacks` count | > 0 (CB 트리핑 확인) |
| `fallback_response_ms` p95 | < 200ms (즉시 반환 확인) |

### 03-recovery
| 지표 | 기대값 |
|------|--------|
| `cb_state_open_count` | > 0 (Phase 1에서 OPEN 관찰) |
| `cb_state_closed_count` | > 0 (Phase 3에서 CLOSED 복귀 확인) |

---

## 응답 패턴 해석

| HTTP 상태 | 응답 시간 | 의미 |
|-----------|-----------|------|
| 200 | 100~500ms | PG SUCCESS, CB CLOSED |
| 400 | 100~500ms | PG LIMIT_EXCEEDED / INVALID_CARD, CB CLOSED |
| 500 (PG body) | 100~500ms | PG INTERNAL_ERROR, CB CLOSED |
| 500 (즉시) | < 50ms | **CB OPEN fallback** (PG 미호출) |

CB OPEN 여부는 `message` 필드로 구분:
- CB fallback: `"PG 결제 요청이 타임아웃되었습니다."` (INTERNAL_ERROR)
- PG 실패: `"PG 결제 요청에 실패했습니다."` (BAD_REQUEST)

---

## 설정 튜닝 실험

`apps/commerce-api/src/main/resources/application.yml` 의 `resilience4j` 섹션 수정:

| 실험 | 변경 설정 | 기대 효과 |
|------|----------|---------|
| 빠른 트리핑 | `failureRateThreshold: 30` | PG ~40% 실패 시 더 빠르게 OPEN |
| 느린 복구 | `waitDurationInOpenState: 60s` | OPEN 상태를 더 오래 유지 |
| 공격적 복구 | `permittedNumberOfCallsInHalfOpenState: 5` | HALF_OPEN에서 더 많은 probe |
| 민감한 윈도우 | `slidingWindowSize: 5` | 더 적은 요청으로 CB 트리핑 |

수정 후 재기동 없이 적용되지 않으므로, 앱을 재시작해야 한다.

---

## 인증 방식

모든 인증 필요 엔드포인트에 헤더 전달:

```
X-Loopers-LoginId: <loginId>
X-Loopers-LoginPw: Password123!
```

테스트 유저는 `setup()` 단계에서 자동 생성된다.
