/**
 * Global teardown for Component tests
 * Runs after all tests complete
 */

import { generateTestReport } from '@chauhaidang/xq-test-utils';
import { logger } from '@chauhaidang/xq-harness-common-kit';
import { closeDbFixture } from './helpers/db-fixture';

export default async (): Promise<void> => {
  logger.info('🧹 Component-Teardown: Running global teardown');

  // Close DatabaseHelper (xq-test-utils) to prevent Jest from hanging
  try {
    await closeDbFixture();
    logger.info('✅ Database connection pool closed');
  } catch (error) {
    logger.warn('⚠️ Could not close database pool:', error);
  }

  // Generate test report
  try {
    await generateTestReport({
      junitXmlPath: './test/component/tsr/junit.xml',
      reportMdPath: './test/component/tsr/report.md',
    });
  } catch (error) {
    logger.warn('⚠️ Could not generate test report:', error);
  }

  logger.info('✅ Component-Teardown: Global teardown complete');
};
