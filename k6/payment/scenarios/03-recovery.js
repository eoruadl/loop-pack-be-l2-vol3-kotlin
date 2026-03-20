/**
 * 03-recovery.js — OPEN → HALF_OPEN → CLOSED 전 사이클 관찰
 *
 * 3단계 stages:
 *   Phase 1 — Burst  (0~20s):  20 VU, CB OPEN 유도
 *   Phase 2 — Wait   (20~55s):  0 VU, OPEN 상태 유지 (waitDurationInOpenState=30s 대기)
 *   Phase 3 — Probe  (55~90s):  5 VU, HALF_OPEN probe → CLOSED 복구 관찰
 *
 * 검증 포인트:
 *   - Phase 1: CB가 OPEN으로 전환 (cb_state_open counter > 0)
 *   - Phase 2: 30초간 모든 요청 즉시 fallback
 *   - Phase 3: 최초 3개 probe 중 성공 시 CLOSED 복귀
 *             (permittedNumberOfCallsInHalfOpenState=3 이므로)
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Gauge } from 'k6/metrics';
import { BASE_URL, authHeaders, getCbState } from '../utils/client.js';
import { runSetup } from '../utils/setup.js';

// ---- Custom metrics -------------------------------------------------------
const cbOpenCount   = new Counter('cb_state_open_count');
const cbHalfOpen    = new Counter('cb_state_half_open_count');
const cbClosed      = new Counter('cb_state_closed_count');
const cbFallbacks   = new Counter('cb_open_fallbacks');
const probeSuccess  = new Counter('half_open_probe_success');

// ---- k6 options -----------------------------------------------------------
export const options = {
  stages: [
    // Phase 1: Burst — trip the CB
    { duration: '20s', target: 20 },
    // Phase 2: Zero load — wait for OPEN → HALF_OPEN transition (waitDuration=30s)
    { duration: '35s', target: 0 },
    // Phase 3: Light probing — trigger HALF_OPEN, observe CLOSED recovery
    { duration: '35s', target: 5 },
  ],
  thresholds: {
    'cb_state_open_count':     ['count > 0'],   // CB must have been observed as OPEN
    'cb_state_closed_count':   ['count > 0'],   // CB must recover to CLOSED
  },
};

// ---- Setup ----------------------------------------------------------------
export function setup() {
  // Phase 1: 20 VU × ~20s = many iterations; Phase 3: 5 VU × 35s
  return runSetup(400);
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
    cardType: 'HYUNDAI',
    cardNo: '1111222233334444',
  });

  // Poll CB state before sending the request
  const stateBefore = getCbState();

  switch (stateBefore) {
    case 'OPEN':      cbOpenCount.add(1);   break;
    case 'HALF_OPEN': cbHalfOpen.add(1);    break;
    case 'CLOSED':    cbClosed.add(1);      break;
  }

  const res = http.post(`${BASE_URL}/api/v1/payments`, payload, { headers });

  // Classify as CB fallback vs real PG response
  const isFallback = (() => {
    if (res.status !== 500) return false;
    try {
      const msg = (res.json().meta.message || '').toLowerCase();
      return msg.includes('circuit breaker') || msg.includes('타임아웃') || msg.includes('timeout');
    } catch (_) { return false; }
  })();

  if (isFallback) {
    cbFallbacks.add(1);
  }

  check(res, {
    'payment_success (200)':  (r) => r.status === 200,
    'pg_fail (400/500)':      (r) => (r.status === 400 || r.status === 500) && !isFallback,
    'cb_fallback (500)':      (_) => isFallback,
  });

  // In HALF_OPEN: track if the probe call succeeded (PG returned SUCCESS)
  if (stateBefore === 'HALF_OPEN' && res.status === 200) {
    probeSuccess.add(1);
    console.log(`[recovery] HALF_OPEN probe SUCCESS — VU=${__VU} orderId=${orderId}`);
  }

  // Progress log every iteration so we can trace the state machine
  console.log(
    `[recovery] VU=${__VU} iter=${__ITER} cb_before=${stateBefore} status=${res.status} fallback=${isFallback}`,
  );

  // Small pause in recovery phase so we don't exhaust orders too quickly
  if (stateBefore === 'CLOSED' || stateBefore === 'HALF_OPEN') {
    sleep(1);
  }
}
