/**
 * Component tests: GET /exercises
 */

import * as db from '../helpers/db-fixture';
import { ApiClient } from '../helpers/api-client';

const BASE_URL = process.env.API_BASE_URL || 'http://localhost:8080/xq-fitness-read-service/api/v1';
const apiClient = new ApiClient(BASE_URL);

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

    const body = await apiClient.getExercises(dayId);
    expect(Array.isArray(body)).toBe(true);
  });

  test('GET /exercises without workoutDayId returns 400', async () => {
    // Generated client requires workoutDayId; use fetch to assert 400 when param is omitted
    const res = await fetch(`${BASE_URL}/exercises`);
    expect(res.status).toBe(400);
  });
});
