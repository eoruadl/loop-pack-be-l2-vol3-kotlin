/**
 * 02-trip.js — Circuit Breaker OPEN 유도
 *
 * 목적: 20 VU burst로 slidingWindow(10) 내 ≥5개 실패를 유도해 CB를 OPEN시킨다.
 *       OPEN 후 모든 요청이 즉시 fallback(INTERNAL_ERROR)으로 반환되는지 확인.
 *
 * 설정: 20 VU, 15초 burst (슬라이딩 윈도우를 빠르게 채움)
 *
 * 검증 포인트:
 *   - OPEN 후 요청이 PgPaymentTimeoutException 메시지와 함께 500으로 실패
 *   - 응답시간이 급감 (PG 호출 없이 즉시 반환 → ~수 ms)
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';
import { BASE_URL, authHeaders, getCbState } from '../utils/client.js';
import { runSetup } from '../utils/setup.js';

// ---- Custom metrics -------------------------------------------------------
const cbOpenFallbacks = new Counter('cb_open_fallbacks');
const pgFailures = new Counter('pg_failures');
const pgSuccesses = new Counter('pg_successes');
const fallbackDuration = new Trend('fallback_response_ms', true);

// ---- k6 options -----------------------------------------------------------
export const options = {
  vus: 20,
  duration: '15s',
  thresholds: {
    // 기대: burst 후 CB OPEN 유도 → 전체 요청의 상당수가 즉시 fallback
    'cb_open_fallbacks': ['count > 0'],
    // CB OPEN fallback: PG 호출은 없지만 앞단 DB 조회(user/order/payment)는 존재
    'fallback_response_ms': ['p(95) < 800'],
  },
};

// ---- Setup ----------------------------------------------------------------
export function setup() {
  // 20 VU × ~15 iter (no sleep) ≒ up to 300 iterations
  // Once CB opens most orders won't be consumed (orders stay PENDING_PAYMENT on fallback)
  return runSetup(300);
}

// ---- VU logic -------------------------------------------------------------
const MAX_VUS = 20;

export default function (data) {
  const { loginId, password, orders } = data;

  const idx = ((__VU - 1) + __ITER * MAX_VUS) % orders.length;
  const orderId = orders[idx];

  const headers = authHeaders(loginId, password);
  const payload = JSON.stringify({
    orderId,
    cardType: 'KB',
    cardNo: '9876543210987654',
  });

  const start = Date.now();
  const res = http.post(`${BASE_URL}/api/v1/payments`, payload, { headers });
  const elapsed = Date.now() - start;

  // Classify response
  const isFallback = (() => {
    if (res.status !== 500) return false;
    try {
      const msg = (res.json().meta.message || '').toLowerCase();
      return msg.includes('circuit breaker') || msg.includes('타임아웃') || msg.includes('timeout');
    } catch (_) { return false; }
  })();

  if (isFallback) {
    cbOpenFallbacks.add(1);
    fallbackDuration.add(elapsed);
  }

  check(res, {
    'payment_success (200)':    (r) => r.status === 200,
    'pg_fail (400/500 normal)': (r) => (r.status === 400 || r.status === 500) && !isFallback,
    'cb_open_fallback (500)':   (_) => isFallback,
  });

  if (res.status === 200) pgSuccesses.add(1);
  else if (!isFallback) pgFailures.add(1);

  // Log CB state on each iteration so we can spot the OPEN transition
  const state = getCbState();
  if (state === 'OPEN' && __ITER % 5 === 0) {
    console.log(
      `[trip] CB is OPEN — VU=${__VU} iter=${__ITER} elapsed=${elapsed}ms status=${res.status}`,
    );
  }

  // No sleep — we want maximum burst to trip the CB quickly
}
