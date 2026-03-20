import http from 'k6/http';
import { sleep } from 'k6';
import { BASE_URL, authHeaders, jsonHeaders } from './client.js';

/**
 * Run setup: create a test user, look up an existing product, create N orders.
 *
 * @param {number} orderCount  Number of PENDING_PAYMENT orders to pre-create.
 * @returns {{ loginId: string, password: string, orders: number[] }}
 */
export function runSetup(orderCount = 100) {
  const ts = Date.now();
  // LoginId: alphanumeric only (matches ^[a-zA-Z0-9]+$)
  const loginId = `cbtest${ts}`;
  const password = 'Password123!';

  // 1. Register user
  const regRes = http.post(
    `${BASE_URL}/api/v1/users`,
    JSON.stringify({
      loginId,
      password,
      name: 'CB Test User',
      birthDate: '1990-01-01',
      email: `${loginId}@test.com`,
    }),
    { headers: jsonHeaders() },
  );

  if (regRes.status !== 200) {
    throw new Error(
      `User registration failed: status=${regRes.status} body=${regRes.body}`,
    );
  }
  console.log(`[setup] User created: loginId=${loginId}`);

  // 2. Get first available product
  const productRes = http.get(`${BASE_URL}/api/v1/products?size=1`, {
    headers: jsonHeaders(),
  });

  if (productRes.status !== 200) {
    throw new Error(
      `Product lookup failed: status=${productRes.status}. ` +
      'Seed at least one product before running the test.',
    );
  }

  const productBody = productRes.json();
  const content = productBody && productBody.data && productBody.data.content;
  if (!content || content.length === 0) {
    throw new Error(
      'No products found. Run the app and seed at least one product first.',
    );
  }
  const productId = content[0].id;
  console.log(`[setup] Using productId=${productId}`);

  // 3. Create N orders (PENDING_PAYMENT)
  const headers = authHeaders(loginId, password);
  const orders = [];

  for (let i = 0; i < orderCount; i++) {
    const orderRes = http.post(
      `${BASE_URL}/api/v1/orders`,
      JSON.stringify({
        items: [{ productId, quantity: 1 }],
      }),
      { headers },
    );

    if (orderRes.status === 200) {
      const orderId = orderRes.json().data.id;
      orders.push(orderId);
    } else {
      console.warn(
        `[setup] Order ${i + 1}/${orderCount} failed: status=${orderRes.status}`,
      );
    }

    // Small pacing to avoid hammering the DB during setup
    if ((i + 1) % 20 === 0) sleep(0.1);
  }

  if (orders.length === 0) {
    throw new Error('Failed to create any orders during setup.');
  }

  console.log(
    `[setup] Done — loginId=${loginId}, productId=${productId}, orders=${orders.length}`,
  );
  return { loginId, password, orders };
}
