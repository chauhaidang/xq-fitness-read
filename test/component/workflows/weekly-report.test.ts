/**
 * Component tests: GET /routines/:id/weekly-report
 */

import { logger } from '@chauhaidang/xq-common-kit';
import * as db from '../helpers/db-fixture';
import { ApiClient } from '../helpers/api-client';

const BASE_URL = process.env.API_BASE_URL || 'http://localhost:8080/xq-fitness-read-service/api/v1';
const apiClient = new ApiClient(BASE_URL);

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

    const body = await apiClient.getWeeklyReport(routineId);
    expect(body.routineId).toBe(routineId);
    expect(body.hasSnapshot).toBe(false);
    expect(body.snapshotCreatedAt).toBeNull();
    expect(Array.isArray(body.muscleGroupTotals)).toBe(true);
    expect(Array.isArray(body.exerciseTotals)).toBe(true);
    body.muscleGroupTotals.forEach((t) => expect(t.totalSets).toBe(0));
  });

  test('GET /routines/:id/weekly-report returns 404 for non-existent routine', async () => {
    await expect(apiClient.getWeeklyReport(999999)).rejects.toMatchObject({
      response: { status: 404 },
    });
  });

  test('GET /routines/:id/weekly-report returns report with snapshot_exercises totals', async () => {
    const weekStart = db.getCurrentWeekStart();
    const routineId = await db.createRoutine('Report Snapshot Routine', null, true);
    routineIdsToClean.push(routineId);
    const dayId = await db.createWorkoutDay(routineId, 1, 'Push Day', null);
    const snapshotId = await db.createSnapshot(routineId, weekStart);
    const snapshotDayId = await db.createSnapshotWorkoutDay(snapshotId, dayId, 1, 'Push Day', null);
    await db.createSnapshotExercise(snapshotDayId, 1, 'Bench Press', 1, 30, 135, 3, null);
    await db.createSnapshotExercise(snapshotDayId, 2, 'Bench Press', 1, 30, 135, 3, null);

    const body = await apiClient.getWeeklyReport(routineId);
    expect(body.routineId).toBe(routineId);
    expect(body.hasSnapshot).toBe(true);
    expect(body.snapshotCreatedAt).toBeDefined();
    expect(Array.isArray(body.exerciseTotals)).toBe(true);
    const bench = body.exerciseTotals.find((e) => e.exerciseName === 'Bench Press');
    expect(bench).toBeDefined();
    expect(bench!.totalReps).toBe(60);
    expect(bench!.totalWeight).toBe(270);
    const chestTotal = body.muscleGroupTotals?.find((t) => t.muscleGroup?.id === 1);
    expect(chestTotal).toBeDefined();
    expect(chestTotal!.totalSets).toBe(6);
  });

  describe('weekStartDate query parameter', () => {
    test('GET /routines/:id/weekly-report with weekStartDate returns snapshot from the specified previous week', async () => {
      const previousWeekStart = db.getPreviousWeekStart(1);
      const currentWeekStart = db.getCurrentWeekStart();

      const routineId = await db.createRoutine('Previous Week Snapshot Routine', null, true);
      routineIdsToClean.push(routineId);
      const dayId = await db.createWorkoutDay(routineId, 1, 'Pull Day', null);

      // Seed previous week snapshot with Deadlift (muscle group 1, 5 sets)
      const prevSnapshotId = await db.createSnapshot(routineId, previousWeekStart);
      const prevSnapshotDayId = await db.createSnapshotWorkoutDay(prevSnapshotId, dayId, 1, 'Pull Day', null);
      await db.createSnapshotExercise(prevSnapshotDayId, 1, 'Deadlift', 1, 25, 225, 5, null);

      // Seed current week snapshot with Bench Press (muscle group 1, 3 sets) — deliberately different
      // data so that if the service ignores weekStartDate and returns the current week, assertions fail.
      const currSnapshotId = await db.createSnapshot(routineId, currentWeekStart);
      const currSnapshotDayId = await db.createSnapshotWorkoutDay(currSnapshotId, dayId, 1, 'Pull Day', null);
      await db.createSnapshotExercise(currSnapshotDayId, 2, 'Bench Press', 1, 40, 135, 3, null);

      const body = await apiClient.getWeeklyReport(routineId, previousWeekStart);

      // Must reflect previous week's snapshot — not the current week's
      expect(body.routineId).toBe(routineId);
      expect(body.weekStartDate).toBe(previousWeekStart);
      expect(body.hasSnapshot).toBe(true);
      expect(body.snapshotCreatedAt).toBeDefined();
      expect(body.exerciseTotals.find((e) => e.exerciseName === 'Bench Press')).toBeUndefined();
      const deadlift = body.exerciseTotals.find((e) => e.exerciseName === 'Deadlift');
      expect(deadlift).toBeDefined();
      expect(deadlift!.totalReps).toBe(25);
      expect(deadlift!.totalWeight).toBe(225);
      const muscleTotal = body.muscleGroupTotals?.find((t) => t.muscleGroup?.id === 1);
      expect(muscleTotal).toBeDefined();
      expect(muscleTotal!.totalSets).toBe(5); // 5 from Deadlift only, not 8 (5+3) from both weeks
    });

    test('GET /routines/:id/weekly-report with weekStartDate returns empty report when no snapshot exists for that previous week', async () => {
      const previousWeekStart = db.getPreviousWeekStart(2);
      const routineId = await db.createRoutine('Previous Week No Snapshot Routine', null, true);
      routineIdsToClean.push(routineId);
      // Seed a snapshot only for the current week — not for the queried previous week
      const currentWeekStart = db.getCurrentWeekStart();
      const dayId = await db.createWorkoutDay(routineId, 1, 'Leg Day', null);
      const snapshotId = await db.createSnapshot(routineId, currentWeekStart);
      const snapshotDayId = await db.createSnapshotWorkoutDay(snapshotId, dayId, 1, 'Leg Day', null);
      await db.createSnapshotExercise(snapshotDayId, 1, 'Squat', 3, 20, 185, 4, null);

      const body = await apiClient.getWeeklyReport(routineId, previousWeekStart);

      expect(body.routineId).toBe(routineId);
      expect(body.weekStartDate).toBe(previousWeekStart);
      expect(body.hasSnapshot).toBe(false);
      expect(body.snapshotCreatedAt).toBeNull();
      expect(Array.isArray(body.muscleGroupTotals)).toBe(true);
      body.muscleGroupTotals.forEach((t) => expect(t.totalSets).toBe(0));
      expect(body.exerciseTotals).toHaveLength(0);
    });
  });
});
