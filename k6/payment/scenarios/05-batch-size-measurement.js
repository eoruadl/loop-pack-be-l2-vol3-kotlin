/**
 * 05-batch-size-measurement.js
 *
 * 목적:
 *   - 주문 API 10,000건 기준 avg / p(95) / p(99) / p(99.9)를 측정한다.
 *   - 대기열/토큰 플로우는 그대로 따르되, 주문 API 호출 자체의 duration만 별도 metric으로 기록한다.
 *
 * 실행 예시:
 *   TOTAL_ITERATIONS=10000 VUS=50 USER_POOL_SIZE=200 k6 run k6/payment/scenarios/05-batch-size-measurement.js
 *
 * 주의:
 *   - 상품 재고는 TOTAL_ITERATIONS 이상 필요하다.
 *   - queue admission batch-size / fixed-delay 설정이 너무 보수적이면 전체 수행 시간이 길어질 수 있다.
 */
import http from 'k6/http';
import exec from 'k6/execution';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { BASE_URL, authHeaders, jsonHeaders } from '../utils/client.js';

const totalIterations = Number(__ENV.TOTAL_ITERATIONS || 10000);
const vus = Number(__ENV.VUS || 50);
const userPoolSize = Number(__ENV.USER_POOL_SIZE || vus);
const queuePollTimeoutSeconds = Number(__ENV.QUEUE_POLL_TIMEOUT_SECONDS || 180);

const orderOnlyDuration = new Trend('order_only_duration', true);
const orderSuccessRate = new Rate('order_success_rate');
const queueAcquireSuccessRate = new Rate('queue_acquire_success_rate');

export const options = {
  setupTimeout: __ENV.SETUP_TIMEOUT || '20m',
  scenarios: {
    batch_size_measurement: {
      executor: 'shared-iterations',
      vus,
      iterations: totalIterations,
      maxDuration: __ENV.MAX_DURATION || '60m',
    },
  },
  thresholds: {
    order_only_duration: [
      'avg < 2000',
      'p(95) < 5000',
      'p(99) < 8000',
      'p(99.9) < 12000',
    ],
    order_success_rate: ['rate > 0.95'],
    queue_acquire_success_rate: ['rate > 0.99'],
  },
};

export function setup() {
  const ts = Date.now();
  const password = 'Password123!';
  const users = [];

  for (let i = 0; i < userPoolSize; i += 1) {
    const loginId = `qload${ts}${i}`;
    const regRes = http.post(
      `${BASE_URL}/api/v1/users`,
      JSON.stringify({
        loginId,
        password,
        name: 'Queue Load User',
        birthDate: '1990-01-01',
        email: `${loginId}@test.com`,
      }),
      { headers: jsonHeaders() },
    );

    if (regRes.status !== 200) {
      throw new Error(`User registration failed: loginId=${loginId} status=${regRes.status} body=${regRes.body}`);
    }

    users.push({ loginId, password });
  }

  const productRes = http.get(`${BASE_URL}/api/v1/products?size=1`, {
    headers: jsonHeaders(),
  });
  if (productRes.status !== 200) {
    throw new Error(`Product lookup failed: status=${productRes.status} body=${productRes.body}`);
  }

  const productBody = productRes.json();
  const content = productBody && productBody.data && productBody.data.content;
  if (!content || content.length === 0) {
    throw new Error('No products found. Seed at least one product with sufficient stock before running the test.');
  }

  return {
    users,
    productId: content[0].id,
  };
}

export default function (data) {
  const user = data.users[(exec.vu.idInTest - 1) % data.users.length];
  const skipQueue = (__ENV.SKIP_QUEUE || 'false').toLowerCase() === 'true';
  const token = skipQueue ? 'bypass-token' : acquireQueueToken(user);

  queueAcquireSuccessRate.add(token != null);
  if (!token) {
    return;
  }

  const headers = authHeaders(user.loginId, user.password);
  if (!skipQueue) {
    headers['X-Queue-Token'] = token;
  }

  const payload = JSON.stringify({
    items: [{ productId: data.productId, quantity: 1 }],
    cardType: 'SAMSUNG',
    cardNo: '1234567890123456',
  });

  const res = http.post(`${BASE_URL}/api/v1/orders`, payload, { headers });
  orderOnlyDuration.add(res.timings.duration);

  const isSuccess = check(res, {
    'order_success (200)': (r) => r.status === 200,
  });
  orderSuccessRate.add(isSuccess ? 1 : 0);
}

function acquireQueueToken(user) {
  const enterRes = http.post(
    `${BASE_URL}/api/v1/queue/enter`,
    null,
    { headers: authHeaders(user.loginId, user.password) },
  );

  if (enterRes.status !== 200 && enterRes.status !== 404) {
    console.warn(`[queue-enter] status=${enterRes.status} loginId=${user.loginId} body=${enterRes.body}`);
    return null;
  }

  const deadline = Date.now() + queuePollTimeoutSeconds * 1000;
  while (Date.now() < deadline) {
    const positionRes = http.get(
      `${BASE_URL}/api/v1/queue/position`,
      { headers: authHeaders(user.loginId, user.password) },
    );

    if (positionRes.status !== 200) {
      console.warn(`[queue-position] status=${positionRes.status} loginId=${user.loginId} body=${positionRes.body}`);
      return null;
    }

    const body = positionRes.json();
    const data = body && body.data;
    const queueToken = data && data.queueToken;
    const retryAfterSeconds = Number((data && data.retryAfterSeconds) || 0);
    const recommendedPollIntervalSeconds = Number((data && data.recommendedPollIntervalSeconds) || 1);

    if (queueToken) {
      if (retryAfterSeconds > 0) {
        sleep(retryAfterSeconds);
      }
      return queueToken;
    }

    sleep(recommendedPollIntervalSeconds);
  }

  console.warn(`[queue-timeout] loginId=${user.loginId} timeout=${queuePollTimeoutSeconds}s`);
  return null;
}
