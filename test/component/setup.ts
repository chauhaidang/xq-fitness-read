/**
 * Global setup for Component tests
 * Waits for API to be ready and initializes DatabaseHelper for fixtures
 */

import { waitForService } from '@chauhaidang/xq-test-utils';
import { logger } from '@chauhaidang/xq-common-kit';
import { initDbFixture, closeDbFixture } from './helpers/db-fixture';

// Get base URL from environment or use default (test-env gateway entry point)
const BASE_URL = process.env.API_BASE_URL || 'http://localhost:8080/xq-fitness-read-service/api/v1';
const HEALTH_CHECK_URL = process.env.HEALTH_CHECK_URL || 'http://localhost:8080/xq-fitness-read-service/health';

// Global setup - runs once before all tests
beforeAll(async () => {
  logger.info('🚀 Starting Component test suite');
  logger.info(`Base URL: ${BASE_URL}`);
  logger.info(`Health Check URL: ${HEALTH_CHECK_URL}`);

  try {
    // Wait for API to be ready
    logger.info('⏳ Waiting for API server to be ready...');
    await waitForService(HEALTH_CHECK_URL, { timeout: 30000, interval: 1000 });
    logger.info('✅ API server is ready');

    // Initialize DB fixture (DatabaseHelper from xq-test-utils)
    await initDbFixture();
    logger.info('✅ Database fixture ready');
  } catch (error) {
    logger.error('❌ API server failed to start within timeout');
    logger.error(`Error: ${error}`);
    throw error;
  }
});

// Close DB fixture in same process as tests (Jest globalTeardown runs in a different process)
afterAll(async () => {
  await closeDbFixture();
  logger.info('🏁 Component test suite completed');
});
