import { Pool, types } from 'pg';
import 'dotenv/config';

// Configure pg to parse int8 (BIGINT) as integers instead of strings
types.setTypeParser(types.builtins.INT8, (val: string) => parseInt(val, 10));

const pool = new Pool({
  host: process.env.DB_HOST || 'localhost',
  port: parseInt(process.env.DB_PORT || '5432', 10),
  database: process.env.DB_NAME || 'xq_fitness',
  user: process.env.DB_USER || 'xq_user',
  password: process.env.DB_PASSWORD || 'xq_password',
  max: 20,
  idleTimeoutMillis: 30000,
  connectionTimeoutMillis: 2000,
  ssl: process.env.DB_SSL !== 'false' ? { rejectUnauthorized: false } : false,
});

pool.on('error', (err: Error) => {
  console.error('Unexpected error on idle client', err);
  process.exit(-1);
});

export const query = (text: string, params?: unknown[]) => pool.query(text, params);
export const getClient = () => pool.connect();
export const close = () => pool.end();

// Default export for require-style imports (used in teardown)
export default { query, getClient, close };
