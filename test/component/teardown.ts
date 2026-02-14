/**
 * Global teardown for Component tests
 * Runs after all tests complete
 */

import { generateTestReport } from '@chauhaidang/xq-test-utils';
import { logger } from '@chauhaidang/xq-common-kit';

export default async (): Promise<void> => {
  logger.info('🧹 Component-Teardown: Running global teardown');

  // Close database connection pool to prevent Jest from hanging
  try {
    // eslint-disable-next-line @typescript-eslint/no-var-requires -- dynamic require for optional DB teardown
    const db = require('../../src/config/database');
    if (db.close) {
      await db.close();
      logger.info('✅ Database connection pool closed');
    }
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
