/**
 * Component tests: GET /routines/:id/weekly-report
 */

import { logger } from '@chauhaidang/xq-common-kit';
import * as db from '../helpers/db-fixture';

const BASE_URL = process.env.API_BASE_URL || 'http://localhost:8080/xq-fitness-read-service/api/v1';

const routineIdsToClean: number[] = [];

afterEach(async () => {
  for (const id of routineIdsToClean) {
    try {
      await db.deleteRoutine(id);
    } catch (e) {
      logger.warn(`Cleanup routine ${id} failed: ${e}`);
    }
  }
  routineIdsToClean.length = 0;
});

describe('Component Test: Weekly Report', () => {
  test('GET /routines/:id/weekly-report returns empty report when no snapshot', async () => {
    const routineId = await db.createRoutine('Report No Snapshot Routine', null, true);
    routineIdsToClean.push(routineId);

    const res = await fetch(`${BASE_URL}/routines/${routineId}/weekly-report`);
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      routineId: number;
      hasSnapshot: boolean;
      snapshotCreatedAt: string | null;
      muscleGroupTotals: { totalSets: number }[];
      exerciseTotals: unknown[];
    };
    expect(body.routineId).toBe(routineId);
    expect(body.hasSnapshot).toBe(false);
    expect(body.snapshotCreatedAt).toBeNull();
    expect(Array.isArray(body.muscleGroupTotals)).toBe(true);
    expect(Array.isArray(body.exerciseTotals)).toBe(true);
    body.muscleGroupTotals.forEach((t) => expect(t.totalSets).toBe(0));
  });

  test('GET /routines/:id/weekly-report returns 404 for non-existent routine', async () => {
    const res = await fetch(`${BASE_URL}/routines/999999/weekly-report`);
    expect(res.status).toBe(404);
  });

  test('GET /routines/:id/weekly-report returns report with snapshot_exercises totals', async () => {
    const weekStart = db.getCurrentWeekStart();
    const routineId = await db.createRoutine('Report Snapshot Routine', null, true);
    routineIdsToClean.push(routineId);
    const dayId = await db.createWorkoutDay(routineId, 1, 'Push Day', null);
    const snapshotId = await db.createSnapshot(routineId, weekStart);
    const snapshotDayId = await db.createSnapshotWorkoutDay(snapshotId, dayId, 1, 'Push Day', null);
    await db.createSnapshotExercise(snapshotDayId, 1, 'Bench Press', 1, 30, 135, 3, null);

    const res = await fetch(`${BASE_URL}/routines/${routineId}/weekly-report`);
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      routineId: number;
      hasSnapshot: boolean;
      snapshotCreatedAt: unknown;
      exerciseTotals: { exerciseName: string; totalReps: number; totalWeight: number }[];
      muscleGroupTotals: { muscleGroup: { id: number }; totalSets: number }[];
    };
    expect(body.routineId).toBe(routineId);
    expect(body.hasSnapshot).toBe(true);
    expect(body.snapshotCreatedAt).toBeDefined();
    expect(Array.isArray(body.exerciseTotals)).toBe(true);
    const bench = body.exerciseTotals.find((e) => e.exerciseName === 'Bench Press');
    expect(bench).toBeDefined();
    expect(bench!.totalReps).toBe(30);
    expect(bench!.totalWeight).toBe(135);
    const chestTotal = body.muscleGroupTotals?.find((t) => t.muscleGroup?.id === 1);
    expect(chestTotal).toBeDefined();
    expect(chestTotal!.totalSets).toBe(3);
  });
});
