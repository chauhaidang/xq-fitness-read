/**
 * Component tests: GET /exercises
 */

import * as db from '../helpers/db-fixture';

const BASE_URL = process.env.API_BASE_URL || 'http://localhost:8080/xq-fitness-read-service/api/v1';

const routineIdsToClean: number[] = [];

afterEach(async () => {
  for (const id of routineIdsToClean) {
    try {
      await db.deleteRoutine(id);
    } catch (_e) {
      // ignore
    }
  }
  routineIdsToClean.length = 0;
});

describe('Component Test: Exercises', () => {
  test('GET /exercises?workoutDayId=X returns 200', async () => {
    const routineId = await db.createRoutine('Exercises Routine', null, true);
    routineIdsToClean.push(routineId);
    const dayId = await db.createWorkoutDay(routineId, 1, 'Push Day', null);

    const res = await fetch(`${BASE_URL}/exercises?workoutDayId=${dayId}`);
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(Array.isArray(body)).toBe(true);
  });

  test('GET /exercises without workoutDayId returns 400', async () => {
    const res = await fetch(`${BASE_URL}/exercises`);
    expect(res.status).toBe(400);
  });
});
