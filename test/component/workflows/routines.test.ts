/**
 * Component tests: GET /routines, GET /routines/:id, GET /routines/:id/days
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

describe('Component Test: Routines', () => {
  test('GET /muscle-groups returns 200 and array', async () => {
    const res = await fetch(`${BASE_URL}/muscle-groups`);
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(Array.isArray(body)).toBe(true);
  });

  test('GET /routines returns routine created via DB', async () => {
    const routineId = await db.createRoutine('Component Test Routine', 'Description', true);
    routineIdsToClean.push(routineId);

    const res = await fetch(`${BASE_URL}/routines`);
    expect(res.status).toBe(200);
    const body = (await res.json()) as { id: number; name: string; isActive: boolean }[];
    expect(Array.isArray(body)).toBe(true);
    const found = body.find((r) => r.id === routineId);
    expect(found).toBeDefined();
    expect(found!.name).toBe('Component Test Routine');
    expect(found!.isActive).toBe(true);
  });

  test('GET /routines?isActive=true returns only active', async () => {
    const activeId = await db.createRoutine('Active Routine', null, true);
    const inactiveId = await db.createRoutine('Inactive Routine', null, false);
    routineIdsToClean.push(activeId, inactiveId);

    const res = await fetch(`${BASE_URL}/routines?isActive=true`);
    expect(res.status).toBe(200);
    const body = (await res.json()) as { id: number; isActive: boolean }[];
    expect(Array.isArray(body)).toBe(true);
    body.forEach((r) => expect(r.isActive).toBe(true));
    expect(body.some((r) => r.id === activeId)).toBe(true);
  });

  test('GET /routines/:id returns 200 with days and exercises', async () => {
    const routineId = await db.createRoutine('Detail Test Routine', null, true);
    routineIdsToClean.push(routineId);
    const dayId = await db.createWorkoutDay(routineId, 1, 'Push Day', null);
    await db.createWorkoutDaySet(dayId, 1, 3, null);

    const res = await fetch(`${BASE_URL}/routines/${routineId}`);
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      id: number;
      name: string;
      workoutDays: { dayName: string; sets: { numberOfSets: number }[] }[];
    };
    expect(body.id).toBe(routineId);
    expect(body.name).toBe('Detail Test Routine');
    expect(Array.isArray(body.workoutDays)).toBe(true);
    expect(body.workoutDays.length).toBe(1);
    expect(body.workoutDays[0].dayName).toBe('Push Day');
    expect(body.workoutDays[0].sets.length).toBe(1);
    expect(body.workoutDays[0].sets[0].numberOfSets).toBe(3);
  });

  test('GET /routines/:id returns 404 for non-existent', async () => {
    const res = await fetch(`${BASE_URL}/routines/999999`);
    expect(res.status).toBe(404);
  });

  test('GET /routines/:id/days returns 200 ordered by dayNumber', async () => {
    const routineId = await db.createRoutine('Days Test Routine', null, true);
    routineIdsToClean.push(routineId);
    await db.createWorkoutDay(routineId, 2, 'Day Two', null);
    await db.createWorkoutDay(routineId, 1, 'Day One', null);

    const res = await fetch(`${BASE_URL}/routines/${routineId}/days`);
    expect(res.status).toBe(200);
    const body = (await res.json()) as { dayNumber: number; dayName: string }[];
    expect(Array.isArray(body)).toBe(true);
    expect(body.length).toBe(2);
    expect(body[0].dayNumber).toBeLessThanOrEqual(body[1].dayNumber);
    expect(body[0].dayName).toBe('Day One');
    expect(body[1].dayName).toBe('Day Two');
  });

  test('GET /routines/:id/days returns 404 for non-existent routine', async () => {
    const res = await fetch(`${BASE_URL}/routines/999999/days`);
    expect(res.status).toBe(404);
  });
});
