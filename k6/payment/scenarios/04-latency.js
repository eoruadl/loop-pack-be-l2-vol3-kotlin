/**
 * 04-latency.js — 결제 API 응답시간 측정 (피크 시간대 100 VU)
 *
 * 목적: POST /api/v1/payments 의 avg / p(95) / p(99) 응답시간을 정량적으로 측정한다.
 *       k6 내장 메트릭 http_req_duration 의 threshold 로 통과/실패를 자동 판정.
 *
 * 설정: 100 VU, 60초, sleep(0) — 피크 시간대 동시 사용자 100명 가정
 */
import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';
import { BASE_URL, authHeaders } from '../utils/client.js';
import { runSetup } from '../utils/setup.js';

// ---- Custom metrics -------------------------------------------------------
const paymentSuccessRate = new Rate('payment_success_rate');

// ---- k6 options ----------------------------------------------------------
export const options = {
  vus: 100,
  duration: '60s',
  thresholds: {
    // 응답시간 목표값
    'http_req_duration': [
      'avg < 500',    // 평균 응답시간 500ms 이하
      'p(95) < 1500', // 95%가 1.5s 이하
      'p(99) < 3000', // 99%가 3s 이하
    ],
  },
};

// ---- Setup ---------------------------------------------------------------
// 100 VU × ~60 iter (sleep 없음) = 최대 6000개 필요 → 여유분 포함 6500개 생성
export function setup() {
  return runSetup(6500);
}

// ---- VU logic ------------------------------------------------------------
const MAX_VUS = 100;

export default function (data) {
  const { loginId, password, orders } = data;

  // 각 VU가 중복 없이 orderId를 사용
  const idx = ((__VU - 1) + __ITER * MAX_VUS) % orders.length;
  const orderId = orders[idx];

  const headers = authHeaders(loginId, password);
  const payload = JSON.stringify({
    orderId,
    cardType: 'SAMSUNG',
    cardNo: '1234567890123456',
  });

  const res = http.post(`${BASE_URL}/api/v1/payments`, payload, { headers });

  const isSuccess = check(res, {
    'payment_success (200)': (r) => r.status === 200,
  });

  // PG 실패(400/500) 도 정상 범주로 허용 — 응답시간만 측정
  check(res, {
    'response_received': (r) => r.status !== 0,
  });

  paymentSuccessRate.add(isSuccess ? 1 : 0);

  // sleep 없음 — 연속 요청으로 실제 부하 상황 재현
}
