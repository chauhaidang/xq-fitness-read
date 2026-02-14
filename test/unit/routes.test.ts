import request from 'supertest';
import express from 'express';
import * as readController from '../../src/controllers/readController';

jest.mock('../../src/services/readService', () => ({
  getAllMuscleGroups: jest.fn().mockResolvedValue([]),
  getAllRoutines: jest.fn().mockResolvedValue([]),
  getRoutineById: jest.fn().mockResolvedValue(null),
  getWorkoutDaysByRoutineId: jest.fn().mockResolvedValue([]),
  getExercisesByWorkoutDayId: jest.fn().mockResolvedValue([]),
}));

const app = express();
app.use(express.json());
app.get('/api/v1/muscle-groups', readController.getMuscleGroups);
app.get('/api/v1/routines', readController.getRoutines);

describe('ReadController', () => {
  test('GET /api/v1/muscle-groups returns 200 and array', async () => {
    const res = await request(app).get('/api/v1/muscle-groups');
    expect(res.status).toBe(200);
    expect(Array.isArray(res.body)).toBe(true);
  });

  test('GET /api/v1/routines returns 200 and array', async () => {
    const res = await request(app).get('/api/v1/routines');
    expect(res.status).toBe(200);
    expect(Array.isArray(res.body)).toBe(true);
  });
});
