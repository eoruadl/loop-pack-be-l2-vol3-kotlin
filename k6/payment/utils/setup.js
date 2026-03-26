import http from 'k6/http';
import { BASE_URL, jsonHeaders } from './client.js';

/**
 * Run setup: create a test user and look up an existing product.
 *
 * @returns {{ loginId: string, password: string, productId: number }}
 */
export function runSetup() {
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

  console.log(`[setup] Done — loginId=${loginId}, productId=${productId}`);
  return { loginId, password, productId };
}
