# 결제 프로세스 흐름 및 장애 대응

## 목차
1. [결제 요청 흐름](#1-결제-요청-흐름)
2. [콜백 처리 흐름](#2-콜백-처리-흐름)
3. [복구 흐름](#3-복구-흐름)
4. [Payment 상태 전이도](#4-payment-상태-전이도)
5. [장애 대응 요약](#5-장애-대응-요약)

---

## 1. 결제 요청 흐름

```mermaid
sequenceDiagram
    actor Client
    participant Controller
    participant PaymentFacade
    participant PaymentService
    participant CircuitBreaker
    participant PgPaymentClient
    participant PG as pg-simulator
    participant DB

    Note over Client,DB: ── 1. 결제 요청 ──

    Client->>Controller: POST /api/v1/payments
    Controller->>PaymentFacade: requestPayment()

    PaymentFacade->>PaymentService: createPayment() [REQUIRES_NEW TX]
    PaymentService->>DB: existsActiveByOrderId?

    alt PENDING/COMPLETED 이미 존재 🔒 중복 방어
        DB-->>PaymentService: true
        PaymentService-->>Client: 409 CONFLICT
    else
        DB-->>PaymentService: false
        PaymentService->>DB: save(PENDING, pgTxId=null)

        Note over PaymentFacade,PG: TX 외부에서 PG 호출
        PaymentFacade->>CircuitBreaker: requestPayment()

        alt ⚡ Circuit OPEN
            CircuitBreaker-->>PaymentFacade: PgPaymentTimeoutException
            Note over DB: PENDING 유지 (pgTxId=null)
            PaymentFacade-->>Client: 500 → 복구 로직에 위임
        else
            CircuitBreaker->>PgPaymentClient: forward
            PgPaymentClient->>PG: POST /api/v1/payments (X-USER-ID 헤더)

            alt 💥 PG 5xx 실패 (40% 확률)
                PG-->>PgPaymentClient: 500
                PgPaymentClient-->>PaymentFacade: PgPaymentFailException
                PaymentFacade->>PaymentService: failPayment() → FAILED
                PaymentFacade-->>Client: 400 Bad Request
            else ⏱ 타임아웃 (3s)
                PgPaymentClient-->>PaymentFacade: PgPaymentTimeoutException
                Note over DB: PENDING 유지 (pgTxId=null)
                PaymentFacade-->>Client: 500 → 복구 로직에 위임
            else ✅ PG 성공
                PG-->>PgPaymentClient: {transactionKey, status: PENDING}
                PgPaymentClient-->>PaymentFacade: PgPaymentResponse
                PaymentFacade->>PaymentService: setPgTransactionId() 저장
                PaymentFacade-->>Client: 200 (PENDING)
            end
        end
    end
```

### 핵심 설계 포인트

| 구간 | 설계 의도 |
|------|---------|
| `REQUIRES_NEW` 트랜잭션으로 Payment 선커밋 | PG 호출 시간 동안 DB 커넥션을 붙잡지 않음 |
| PG 호출을 트랜잭션 외부에서 실행 | PG 실패가 내부 DB 롤백을 유발하지 않음 |
| `existsActiveByOrderId` 중복 체크 | 동일 주문에 PENDING/COMPLETED 결제가 이미 있으면 CONFLICT |
| FAILED 상태는 "활성"으로 간주하지 않음 | PG 실패 후 재결제 허용 |

---

## 2. 콜백 처리 흐름

```mermaid
sequenceDiagram
    participant PG as pg-simulator
    participant Controller
    participant PaymentFacade
    participant PaymentService
    participant OrderService
    participant DB

    Note over PG,DB: ── 2. 콜백 수신 (1~5초 후 PG → Commerce) ──

    PG->>Controller: POST /api/v1/payments/callback
    Note right of PG: {transactionKey, status, reason}

    Controller->>PaymentFacade: handleCallback(pgTransactionId, pgStatus, reason)
    PaymentFacade->>DB: findByPgTransactionId()

    alt 🔁 이미 종료된 결제 (중복 콜백) — 멱등성 처리
        Note over PaymentFacade: status != PENDING → early return
        PaymentFacade-->>PG: 200 OK (무시)
    else
        alt pgStatus == "SUCCESS"
            PaymentFacade->>PaymentService: completePayment() → COMPLETED
            PaymentFacade->>OrderService: payOrder() → PAID
            PaymentFacade-->>PG: 200 OK
        else pgStatus == "FAILED"
            Note over PaymentFacade: parseFailureCode(reason) 🗝
            alt reason == "한도초과입니다. ..."
                PaymentFacade->>PaymentService: failPayment(LIMIT_EXCEEDED)
            else reason == "잘못된 카드입니다. ..."
                PaymentFacade->>PaymentService: failPayment(INVALID_CARD)
            else 그 외
                PaymentFacade->>PaymentService: failPayment(FAILED)
            end
            PaymentFacade-->>PG: 200 OK
        end
    end
```

### 핵심 설계 포인트

| 구간 | 설계 의도 |
|------|---------|
| `status != PENDING` early return | PG 콜백 재전송 시 중복 처리 방지, 항상 200 반환 |
| `parseFailureCode(reason)` | PG reason 문자열 파싱을 한 곳에 응집, `contains` 대신 정확한 `==` 매칭 |
| `PgFailureCode` enum | 애플리케이션 레이어가 PG 원문 문자열을 알지 못하도록 경계에서 타입 변환 |

---

## 3. 복구 흐름

```mermaid
sequenceDiagram
    actor Client
    participant Controller
    participant RecoveryFacade as PaymentRecoveryFacade
    participant PaymentService
    participant PgPaymentClient
    participant PG as pg-simulator
    participant OrderService

    Note over Client,OrderService: ── 3. 복구 흐름 ──

    rect rgb(240, 248, 255)
        Note left of Client: 수동 복구
        Client->>Controller: POST /api/v1/payments/{id}/recover
        Controller->>RecoveryFacade: recoverPayment(paymentId)
    end

    rect rgb(255, 248, 240)
        Note left of Client: 자동 복구 (배치)
        Note over RecoveryFacade: recoverPendingPayments(olderThan=30s)<br/>PENDING 목록 순회
    end

    RecoveryFacade->>PaymentService: getPaymentById()
    Note over RecoveryFacade: status != PENDING → 즉시 return

    alt pgTxId 존재
        RecoveryFacade->>PgPaymentClient: getPayment(pgTxId, userId)
        PgPaymentClient->>PG: GET /api/v1/payments/{txKey} (X-USER-ID)
    else pgTxId null (타임아웃 시나리오)
        RecoveryFacade->>PgPaymentClient: getPaymentByOrderId(orderId, userId)
        PgPaymentClient->>PG: GET /api/v1/payments?orderId=ORDER-{id}
        Note over PgPaymentClient: transactions 빈 배열 → null 반환<br/>→ failPayment(FAILED)
    end

    PG-->>PgPaymentClient: {status, reason}
    Note over PgPaymentClient: parseFailureCode(reason) → failureCode

    alt status == "SUCCESS"
        RecoveryFacade->>PaymentService: completePayment() → COMPLETED
        RecoveryFacade->>OrderService: payOrder() → PAID
    else status == "FAILED"
        Note over RecoveryFacade: failureCode 기반 분기
        RecoveryFacade->>PaymentService: failPayment(LIMIT_EXCEEDED / INVALID_CARD / FAILED)
    else status == "PENDING"
        Note over RecoveryFacade: 대기 유지 — 다음 배치에서 재시도
    end
```

### 핵심 설계 포인트

| 구간 | 설계 의도 |
|------|---------|
| pgTxId 유무로 조회 방식 분기 | 타임아웃으로 pgTxId 저장 전 실패한 경우도 orderId 기반으로 복구 가능 |
| `status != PENDING` early return | 이미 종료된 결제에 recover 중복 호출 시 안전 |
| 수동 + 자동 두 진입점 | 수동(`POST /{id}/recover`)은 즉시 복구, 자동(`recoverPendingPayments`)은 30초 초과 PENDING 일괄 처리 |
| Circuit OPEN 시 `log.warn` 흡수 | 복구 실패가 다른 결제 복구를 막지 않음 |

---

## 4. Payment 상태 전이도

```mermaid
stateDiagram-v2
    direction LR

    [*] --> PENDING : createPayment()\nDB 선커밋

    PENDING --> PENDING : PG 타임아웃\nCircuit OPEN\n(pgTxId = null 유지)

    PENDING --> FAILED_SAVE : PG 4xx/5xx
    FAILED_SAVE --> FAILED : failPayment()

    PENDING --> WITH_TX : PG 성공\nsetPgTransactionId()

    WITH_TX --> COMPLETED : 콜백 SUCCESS\n또는 recover SUCCESS\n+ orderService.payOrder()

    WITH_TX --> LIMIT_EXCEEDED : 콜백·recover\nreason=한도초과
    WITH_TX --> INVALID_CARD : 콜백·recover\nreason=잘못된카드
    WITH_TX --> FAILED : 콜백·recover\nFAILED (기타)

    PENDING --> COMPLETED : recover\n(pgTxId null → orderId 조회)
    PENDING --> FAILED : recover\ntransactions 없음

    COMPLETED --> COMPLETED : 중복 콜백 무시\n(멱등성)
    FAILED --> FAILED : 중복 콜백 무시\n(멱등성)

    COMPLETED --> [*]
    FAILED --> [*]
    LIMIT_EXCEEDED --> [*]
    INVALID_CARD --> [*]

    note right of WITH_TX
        pgTxId 저장 완료 상태
        (PENDING + pgTxId != null)
    end note
```

---

## 5. 장애 대응 요약

| # | 장애 시나리오 | 내부 상태 | 대응 방법 |
|---|-------------|---------|---------|
| 1 | PG 5xx 실패 (40% 확률) | PENDING → FAILED | `PgPaymentFailException` catch → `failPayment()` |
| 2 | PG 응답 타임아웃 (3s) | PENDING 유지 (pgTxId=null) | 복구 로직에 위임, 클라이언트 500 반환 |
| 3 | Circuit Breaker OPEN | PENDING 유지 (pgTxId=null) | fallback → `PgPaymentTimeoutException`, 복구 위임 |
| 4 | `setPgTransactionId` 저장 전 장애 | PENDING (pgTxId=null) | recover 시 `getPaymentByOrderId`로 복구 |
| 5 | PG 콜백 중복 수신 | 이미 COMPLETED/FAILED | `status != PENDING` early return → 200 반환 (멱등) |
| 6 | 동일 주문 결제 중복 요청 | 첫 번째 PENDING 유지 | `existsActiveByOrderId` → 409 CONFLICT |
| 7 | PG reason 문자열 변경 | — | `parseFailureCode` 단일 함수에서만 파싱, 수정 지점 1개 |
| 8 | 복구 중 PG 재장애 | PENDING 유지 | `recoverPendingPayments`에서 예외 흡수 → 다음 배치 재시도 |

### Circuit Breaker 설정

```yaml
resilience4j:
  circuitbreaker:
    instances:
      pg-payment:
        slidingWindowSize: 10          # 최근 10회 호출 기준
        failureRateThreshold: 50       # 50% 이상 실패 시 OPEN
        waitDurationInOpenState: 30s   # 30초 후 HALF_OPEN 전환
        permittedNumberOfCallsInHalfOpenState: 3
  timelimiter:
    instances:
      pg-payment:
        timeoutDuration: 3s            # 3초 초과 시 타임아웃
```
