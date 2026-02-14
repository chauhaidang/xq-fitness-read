/**
 * Database fixture for read-service component tests.
 * Uses DatabaseHelper from @chauhaidang/xq-test-utils; seeds data and cleans up.
 * Call initDbFixture() in setup and closeDbFixture() in teardown.
 *
 * Component tests always run against local xq-infra (localhost:5432).
 */

import { DatabaseHelper, type DatabaseConfig } from './database';

/** DB config for local xq-infra test environment. */
const DB_CONFIG: DatabaseConfig = {
  host: 'localhost',
  port: 5432,
  database: 'xq_fitness',
  user: 'xq_user',
  password: 'xq_password',
  ssl: false,
};

let dbHelper: DatabaseHelper | null = null;

function getHelper(): DatabaseHelper {
  if (!dbHelper) {
    throw new Error('db-fixture not initialized: call initDbFixture() in setup');
  }
  return dbHelper;
}

async function query(text: string, params?: unknown[]): Promise<{ rows: unknown[] }> {
  return getHelper().query(text, params);
}

/**
 * Initialize the fixture with a connected DatabaseHelper. Call from global setup.
 */
export async function initDbFixture(): Promise<void> {
  if (dbHelper) return;
  dbHelper = new DatabaseHelper(DB_CONFIG);
  await dbHelper.connect();
  const healthCheck = await dbHelper.healthCheck([
    'workout_routines',
    'workout_days',
    'weekly_snapshots',
    'snapshot_exercises',
  ]);
  if (!healthCheck.healthy) {
    throw new Error(
      `Database health check failed. Connection: ${healthCheck.connection}, Schema: ${healthCheck.schema}`
    );
  }
}

/**
 * Close the DatabaseHelper. Call from global teardown.
 */
export async function closeDbFixture(): Promise<void> {
  if (dbHelper) {
    await dbHelper.disconnect();
    dbHelper = null;
  }
}

export function getCurrentWeekStart(): string {
  const d = new Date();
  const utcDay = d.getUTCDay();
  const daysToSubtract = utcDay === 0 ? 6 : utcDay - 1;
  d.setUTCDate(d.getUTCDate() - daysToSubtract);
  return d.toISOString().slice(0, 10);
}

export async function createRoutine(
  name: string,
  description: string | null,
  isActive: boolean
): Promise<number> {
  const result = await query(
    'INSERT INTO workout_routines (name, description, is_active, created_at, updated_at) VALUES ($1, $2, $3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id',
    [name, description, isActive]
  );
  return (result.rows[0] as { id: number }).id;
}

export async function createWorkoutDay(
  routineId: number,
  dayNumber: number,
  dayName: string,
  notes: string | null
): Promise<number> {
  const result = await query(
    'INSERT INTO workout_days (routine_id, day_number, day_name, notes, created_at, updated_at) VALUES ($1, $2, $3, $4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id',
    [routineId, dayNumber, dayName, notes]
  );
  return (result.rows[0] as { id: number }).id;
}

export async function createWorkoutDaySet(
  workoutDayId: number,
  muscleGroupId: number,
  numberOfSets: number,
  notes: string | null
): Promise<number> {
  const result = await query(
    'INSERT INTO workout_day_sets (workout_day_id, muscle_group_id, number_of_sets, notes, created_at, updated_at) VALUES ($1, $2, $3, $4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id',
    [workoutDayId, muscleGroupId, numberOfSets, notes]
  );
  return (result.rows[0] as { id: number }).id;
}

export async function createSnapshot(routineId: number, weekStart: string): Promise<number> {
  const result = await query(
    'INSERT INTO weekly_snapshots (routine_id, week_start_date, created_at, updated_at) VALUES ($1, $2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id',
    [routineId, weekStart]
  );
  return (result.rows[0] as { id: number }).id;
}

export async function createSnapshotWorkoutDay(
  snapshotId: number,
  originalWorkoutDayId: number,
  dayNumber: number,
  dayName: string,
  notes: string | null
): Promise<number> {
  const result = await query(
    'INSERT INTO snapshot_workout_days (snapshot_id, original_workout_day_id, day_number, day_name, notes, created_at) VALUES ($1, $2, $3, $4, $5, CURRENT_TIMESTAMP) RETURNING id',
    [snapshotId, originalWorkoutDayId, dayNumber, dayName, notes]
  );
  return (result.rows[0] as { id: number }).id;
}

export async function createSnapshotWorkoutDaySet(
  snapshotWorkoutDayId: number,
  originalWorkoutDaySetId: number,
  muscleGroupId: number,
  numberOfSets: number,
  notes: string | null
): Promise<number> {
  const result = await query(
    'INSERT INTO snapshot_workout_day_sets (snapshot_workout_day_id, original_workout_day_set_id, muscle_group_id, number_of_sets, notes, created_at) VALUES ($1, $2, $3, $4, $5, CURRENT_TIMESTAMP) RETURNING id',
    [snapshotWorkoutDayId, originalWorkoutDaySetId, muscleGroupId, numberOfSets, notes]
  );
  return (result.rows[0] as { id: number }).id;
}

export async function createSnapshotExercise(
  snapshotWorkoutDayId: number,
  originalExerciseId: number,
  exerciseName: string,
  muscleGroupId: number,
  totalReps: number,
  weight: number,
  totalSets: number,
  notes: string | null
): Promise<number> {
  const result = await query(
    'INSERT INTO snapshot_exercises (snapshot_workout_day_id, original_exercise_id, exercise_name, muscle_group_id, total_reps, weight, total_sets, notes, created_at) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, CURRENT_TIMESTAMP) RETURNING id',
    [snapshotWorkoutDayId, originalExerciseId, exerciseName, muscleGroupId, totalReps, weight, totalSets, notes]
  );
  return (result.rows[0] as { id: number }).id;
}

export async function routineExists(routineId: number): Promise<boolean> {
  const result = await query('SELECT 1 FROM workout_routines WHERE id = $1', [routineId]);
  return result.rows.length > 0;
}

export async function deleteRoutine(routineId: number): Promise<void> {
  await query(
    'DELETE FROM snapshot_exercises WHERE snapshot_workout_day_id IN (SELECT id FROM snapshot_workout_days WHERE snapshot_id IN (SELECT id FROM weekly_snapshots WHERE routine_id = $1))',
    [routineId]
  );
  await query(
    'DELETE FROM snapshot_workout_day_sets WHERE snapshot_workout_day_id IN (SELECT id FROM snapshot_workout_days WHERE snapshot_id IN (SELECT id FROM weekly_snapshots WHERE routine_id = $1))',
    [routineId]
  );
  await query(
    'DELETE FROM snapshot_workout_days WHERE snapshot_id IN (SELECT id FROM weekly_snapshots WHERE routine_id = $1)',
    [routineId]
  );
  await query('DELETE FROM weekly_snapshots WHERE routine_id = $1', [routineId]);
  await query('DELETE FROM workout_day_sets WHERE workout_day_id IN (SELECT id FROM workout_days WHERE routine_id = $1)', [
    routineId,
  ]);
  await query('DELETE FROM workout_days WHERE routine_id = $1', [routineId]);
  await query('DELETE FROM workout_routines WHERE id = $1', [routineId]);
}
