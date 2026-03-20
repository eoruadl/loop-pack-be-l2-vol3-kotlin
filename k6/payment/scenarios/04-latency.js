/**
 * 04-latency.js — 주문+결제 API 응답시간 측정 (피크 시간대 100 VU)
 *
 * 목적: POST /api/v1/orders (주문+결제 통합) 의 avg / p(95) / p(99) 응답시간을 정량적으로 측정한다.
 *       k6 내장 메트릭 http_req_duration 의 threshold 로 통과/실패를 자동 판정.
 *
 * 설정: 100 VU, 60초, sleep(0) — 피크 시간대 동시 사용자 100명 가정
 * 주의: 테스트 환경의 상품 재고가 충분해야 함 (VU × 이터레이션 수 이상)
 */
import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';
import { BASE_URL, authHeaders } from '../utils/client.js';
import { runSetup } from '../utils/setup.js';

// ---- Custom metrics -------------------------------------------------------
const orderSuccessRate = new Rate('order_success_rate');

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
export function setup() {
  return runSetup();
}

// ---- VU logic ------------------------------------------------------------
export default function (data) {
  const { loginId, password, productId } = data;

  const headers = authHeaders(loginId, password);
  const payload = JSON.stringify({
    items: [{ productId, quantity: 1 }],
    cardType: 'SAMSUNG',
    cardNo: '1234567890123456',
  });

  const res = http.post(`${BASE_URL}/api/v1/orders`, payload, { headers });

  const isSuccess = check(res, {
    'order_success (200)': (r) => r.status === 200,
  });

  // PG 실패(400/500) 도 정상 범주로 허용 — 응답시간만 측정
  check(res, {
    'response_received': (r) => r.status !== 0,
  });

  orderSuccessRate.add(isSuccess ? 1 : 0);

  // sleep 없음 — 연속 요청으로 실제 부하 상황 재현
}
