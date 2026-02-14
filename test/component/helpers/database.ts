/**
 * Database utilities for component tests.
 *
 * We intentionally re-export from @chauhaidang/xq-test-utils to avoid
 * duplicating boilerplate (pool wiring, env var defaults, schema checks, etc.).
 */

export { DatabaseHelper, type DatabaseConfig } from '@chauhaidang/xq-test-utils';
