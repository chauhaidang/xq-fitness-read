/**
 * Component tests: GET /routines/:id/weekly-report
 */

import { logger } from '@chauhaidang/xq-harness-common-kit';
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
    await db.createSnapshotExercise(snapshotDayId, 2, 'Bench Press', 1, 40, 145, 3, null);

    const body = await apiClient.getWeeklyReport(routineId);
    expect(body.routineId).toBe(routineId);
    expect(body.hasSnapshot).toBe(true);
    expect(body.snapshotCreatedAt).toBeDefined();
    expect(Array.isArray(body.exerciseTotals)).toBe(true);
    const bench = body.exerciseTotals.find((e) => e.exerciseName === 'Bench Press');
    expect(bench).toBeDefined();
    expect(bench!.totalReps).toBe(40);
    expect(bench!.totalWeight).toBe(145);
    const chestTotal = body.muscleGroupTotals?.find((t) => t.muscleGroup?.id === 1);
    expect(chestTotal).toBeDefined();
    expect(chestTotal!.totalSets).toBe(6);
  });

  test('GET /routines/:id/weekly-report calculates progressive overload against previous week', async () => {
    const routineId = await db.createRoutine('Progressive Overload Routine', null, true);
    routineIdsToClean.push(routineId);

    const currentWeekStart = db.getCurrentWeekStart();
    const previousWeekStart = db.getPreviousWeekStart(1);

    const dayId = await db.createWorkoutDay(routineId, 1, 'Full Body', null);

    // Previous week snapshot
    const prevSnapshotId = await db.createSnapshot(routineId, previousWeekStart);
    const prevSnapshotDayId = await db.createSnapshotWorkoutDay(prevSnapshotId, dayId, 1, 'Full Body', null);

    await db.createSnapshotExercise(prevSnapshotDayId, 1, 'Bench Press', 1, 30, 135, 3, null);
    await db.createSnapshotExercise(prevSnapshotDayId, 2, 'Squat', 3, 20, 200, 4, null);
    await db.createSnapshotExercise(prevSnapshotDayId, 3, 'Deadlift', 4, 15, 225, 3, null);

    // Current week snapshot
    const currSnapshotId = await db.createSnapshot(routineId, currentWeekStart);
    const currSnapshotDayId = await db.createSnapshotWorkoutDay(currSnapshotId, dayId, 1, 'Full Body', null);

    // Bench Press (Reps INCREASED: 35>30, Weight DECREASED: 130<135)
    await db.createSnapshotExercise(currSnapshotDayId, 4, 'Bench Press', 1, 35, 130, 3, null);
    // Squat (Reps MAINTAINED: 20=20, Weight INCREASED: 210>200)
    await db.createSnapshotExercise(currSnapshotDayId, 5, 'Squat', 3, 20, 210, 4, null);
    // Deadlift (Reps DECREASED: 10<15, Weight MAINTAINED: 225=225)
    await db.createSnapshotExercise(currSnapshotDayId, 6, 'Deadlift', 4, 10, 225, 3, null);
    // Lunges (Exists only in current week -> MAINTAINED)
    await db.createSnapshotExercise(currSnapshotDayId, 7, 'Lunges', 3, 40, 50, 4, null);

    const body = await apiClient.getWeeklyReport(routineId, currentWeekStart);

    expect(body.hasSnapshot).toBe(true);
    expect(body.exerciseTotals).toHaveLength(4);

    const bench = body.exerciseTotals.find((e: any) => e.exerciseName === 'Bench Press')!;
    expect(bench.progressStatusRep).toBe('INCREASED');
    expect(bench.progressStatusWeight).toBe('DECREASED');

    const squat = body.exerciseTotals.find((e: any) => e.exerciseName === 'Squat')!;
    expect(squat.progressStatusRep).toBe('MAINTAINED');
    expect(squat.progressStatusWeight).toBe('INCREASED');

    const deadlift = body.exerciseTotals.find((e: any) => e.exerciseName === 'Deadlift')!;
    expect(deadlift.progressStatusRep).toBe('DECREASED');
    expect(deadlift.progressStatusWeight).toBe('MAINTAINED');

    const lunges = body.exerciseTotals.find((e: any) => e.exerciseName === 'Lunges')!;
    expect(lunges.progressStatusRep).toBe('MAINTAINED');
    expect(lunges.progressStatusWeight).toBe('MAINTAINED');
  });

  test('GET /routines/:id/weekly-report calculates progressive overload using max values from both weeks', async () => {
    const routineId = await db.createRoutine('Max-Value Overload Routine', null, true);
    routineIdsToClean.push(routineId);
    
    const currentWeekStart = db.getCurrentWeekStart();
    const previousWeekStart = db.getPreviousWeekStart(1);
    
    const dayId = await db.createWorkoutDay(routineId, 1, 'Full Body', null);
    
    // Previous week snapshot with multiple sessions
    const prevSnapshotId = await db.createSnapshot(routineId, previousWeekStart);
    const prevSnapshotDayId = await db.createSnapshotWorkoutDay(prevSnapshotId, dayId, 1, 'Full Body', null);
    
    // Session 1: 30 reps, 135 weight
    await db.createSnapshotExercise(prevSnapshotDayId, 1, 'Bench Press', 1, 30, 135, 3, null);
    // Session 2: 25 reps, 140 weight (Prev Max: Reps=30, Weight=140)
    await db.createSnapshotExercise(prevSnapshotDayId, 2, 'Bench Press', 1, 25, 140, 3, null);

    // Current week snapshot with multiple sessions
    const currSnapshotId = await db.createSnapshot(routineId, currentWeekStart);
    const currSnapshotDayId = await db.createSnapshotWorkoutDay(currSnapshotId, dayId, 1, 'Full Body', null);
    
    // Session 1: 35 reps, 130 weight
    await db.createSnapshotExercise(currSnapshotDayId, 3, 'Bench Press', 1, 35, 130, 3, null);
    // Session 2: 32 reps, 145 weight (Curr Max: Reps=35, Weight=145)
    await db.createSnapshotExercise(currSnapshotDayId, 4, 'Bench Press', 1, 32, 145, 3, null);

    const body = await apiClient.getWeeklyReport(routineId, currentWeekStart);
    
    const bench = body.exerciseTotals.find((e: any) => e.exerciseName === 'Bench Press')!;
    // Comparison:
    // Prev: Session 2 (140 weight > 135) wins -> Reps=25, Weight=140
    // Curr: Session 2 (145 weight > 130) wins -> Reps=32, Weight=145
    // Reps: Curr (32) > Prev (25) -> INCREASED
    // Weight: Curr (145) > Prev (140) -> INCREASED
    expect(bench.progressStatusRep).toBe('INCREASED');
    expect(bench.progressStatusWeight).toBe('INCREASED');
    expect(bench.totalReps).toBe(32);
    expect(bench.totalWeight).toBe(145);
  });

  test('GET /routines/:id/weekly-report sets progress status to MAINTAINED without previous snapshot', async () => {
    const routineId = await db.createRoutine('No Prev Snapshot Routine', null, true);
    routineIdsToClean.push(routineId);

    const currentWeekStart = db.getCurrentWeekStart();
    const dayId = await db.createWorkoutDay(routineId, 1, 'Push Day', null);

    const snapshotId = await db.createSnapshot(routineId, currentWeekStart);
    const snapshotDayId = await db.createSnapshotWorkoutDay(snapshotId, dayId, 1, 'Push Day', null);
    await db.createSnapshotExercise(snapshotDayId, 1, 'Bench Press', 1, 30, 135, 3, null);

    const body = await apiClient.getWeeklyReport(routineId, currentWeekStart);

    expect(body.hasSnapshot).toBe(true);
    const bench = body.exerciseTotals.find((e: any) => e.exerciseName === 'Bench Press')!;
    expect(bench.progressStatusRep).toBe('MAINTAINED');
    expect(bench.progressStatusWeight).toBe('MAINTAINED');
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
