/**
 * 01-baseline.js — 정상 동작 확인
 *
 * 목적: CircuitBreaker가 CLOSED 상태에서 PG 실패 응답이
 *       PgPaymentFailException(400/500) 으로 내려오는지 확인한다.
 *       PgPaymentTimeoutException(503) 과 구분되는지 함께 검증.
 *
 * 설정: 5 VU, 30초, 저부하
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';
import { BASE_URL, authHeaders, getCbState } from '../utils/client.js';
import { runSetup } from '../utils/setup.js';

// ---- Custom metrics -------------------------------------------------------
const cbOpenRate = new Rate('cb_open_rate');

// ---- k6 options ----------------------------------------------------------
export const options = {
  vus: 5,
  duration: '30s',
  thresholds: {
    // CB OPEN fallback 은 baseline 에서 드물어야 함
    'cb_open_rate': ['rate < 0.1'],
    http_req_duration: ['p(95) < 2000'],
  },
};

// ---- Setup ---------------------------------------------------------------
// setup() runs once before VUs start; returned data is passed to default().
export function setup() {
  // 5 VU × ~30 iter/VU (throttled) = max ~150 orders needed
  return runSetup(200);
}

// ---- VU logic ------------------------------------------------------------
const MAX_VUS = 5;

export default function (data) {
  const { loginId, password, orders } = data;

  // Index: each VU cycles through its slice of orders
  const idx = ((__VU - 1) + __ITER * MAX_VUS) % orders.length;
  const orderId = orders[idx];

  const headers = authHeaders(loginId, password);
  const payload = JSON.stringify({
    orderId,
    cardType: 'SAMSUNG',
    cardNo: '1234567890123456',
  });

  const res = http.post(`${BASE_URL}/api/v1/payments`, payload, { headers });

  const isPgFail = check(res, {
    'pg_fail (PG error → 400/500)': (r) =>
      r.status === 400 || r.status === 500,
  });

  const isCbOpen = check(res, {
    'cb_open (CB open fallback → 500 INTERNAL)': (r) => {
      if (r.status !== 500) return false;
      try {
        const msg = (r.json().meta.message || '').toLowerCase();
        return msg.includes('circuit breaker') || msg.includes('타임아웃') || msg.includes('timeout');
      } catch (_) { return false; }
    },
  });

  // Success is also valid (PG simulator returns SUCCESS 60% of the time)
  check(res, {
    'payment_success (200)': (r) => r.status === 200,
  });

  cbOpenRate.add(isCbOpen ? 1 : 0);

  if (isCbOpen) {
    // CB should NOT be open frequently in baseline
    console.warn(`[baseline] CB OPEN fallback detected — VU=${__VU} iter=${__ITER}`);
  }

  // Log CB state every 10th iteration per VU
  if (__ITER % 10 === 0) {
    const state = getCbState();
    console.log(`[baseline] VU=${__VU} iter=${__ITER} status=${res.status} cb_state=${state}`);
  }

  // Gentle pacing — keeps RPS low enough to stay in baseline territory
  sleep(1);
}
