/**
 * Component Test: Health endpoint
 */

import { logger } from '@chauhaidang/xq-common-kit';

const BASE_URL = process.env.API_BASE_URL || 'http://localhost:8080/xq-fitness-read-service/api/v1';
const HEALTH_URL = process.env.HEALTH_CHECK_URL || 'http://localhost:8080/xq-fitness-read-service/health';

describe('Component Test: Health', () => {
  test('health endpoint returns 200', async () => {
    const res = await fetch(HEALTH_URL);
    expect(res.status).toBe(200);
    const body = (await res.json()) as { status: string; service: string };
    expect(body.status).toBe('UP');
    expect(body.service).toBe('xq-fitness-read-service');
    logger.info('✅ Health check passed');
  });

  test('muscle-groups endpoint returns 200', async () => {
    const res = await fetch(`${BASE_URL}/muscle-groups`);
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(Array.isArray(body)).toBe(true);
    logger.info('✅ Muscle groups check passed');
  });
});
