import http from 'k6/http';
import { check } from 'k6';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
export const ACTUATOR_URL = __ENV.ACTUATOR_URL || 'http://localhost:8081';

export function authHeaders(loginId, password) {
  return {
    'Content-Type': 'application/json',
    'X-Loopers-LoginId': loginId,
    'X-Loopers-LoginPw': password,
  };
}

export function jsonHeaders() {
  return { 'Content-Type': 'application/json' };
}

/**
 * Check HTTP response and log on failure.
 * Returns true if the check passed.
 */
export function assertSuccess(res, tag) {
  const ok = check(res, {
    [`${tag} status 200`]: (r) => r.status === 200,
  });
  if (!ok) {
    console.warn(`[${tag}] Unexpected status=${res.status} body=${res.body}`);
  }
  return ok;
}

/**
 * Poll the actuator circuit breaker state for 'pg-payment'.
 * Returns the state string (CLOSED / OPEN / HALF_OPEN) or 'UNKNOWN'.
 */
export function getCbState() {
  const res = http.get(`${ACTUATOR_URL}/actuator/circuitbreakers`, {
    headers: { 'Accept': 'application/json' },
    timeout: '3s',
  });
  if (res.status !== 200) return 'UNKNOWN';
  try {
    const body = res.json();
    const cb = body.circuitBreakers && body.circuitBreakers['pg-payment'];
    return cb ? cb.state : 'UNKNOWN';
  } catch (_) {
    return 'UNKNOWN';
  }
}
