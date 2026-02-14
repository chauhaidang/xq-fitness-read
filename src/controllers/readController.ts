import type { Request, Response } from 'express';
import * as readService from '../services/readService';

export async function getMuscleGroups(_req: Request, res: Response): Promise<void> {
  const data = await readService.getAllMuscleGroups();
  res.json(data);
}

export async function getRoutines(req: Request, res: Response): Promise<void> {
  const isActiveParam = req.query.isActive;
  let isActive: boolean | undefined;
  if (isActiveParam === 'true') isActive = true;
  else if (isActiveParam === 'false') isActive = false;
  const data = await readService.getAllRoutines(isActive);
  res.json(data);
}

export async function getRoutineById(req: Request, res: Response): Promise<void> {
  const routineId = parseInt(String(req.params.routineId), 10);
  if (Number.isNaN(routineId)) {
    res.status(400).json({ code: 'BAD_REQUEST', message: 'Invalid routineId', timestamp: new Date().toISOString() });
    return;
  }
  const data = await readService.getRoutineById(routineId);
  if (!data) {
    res.status(404).json({ code: 'NOT_FOUND', message: 'Routine not found', timestamp: new Date().toISOString() });
    return;
  }
  res.json(data);
}

export async function getWorkoutDays(req: Request, res: Response): Promise<void> {
  const routineId = parseInt(String(req.params.routineId), 10);
  if (Number.isNaN(routineId)) {
    res.status(400).json({ code: 'BAD_REQUEST', message: 'Invalid routineId', timestamp: new Date().toISOString() });
    return;
  }
  const data = await readService.getWorkoutDaysByRoutineId(routineId);
  if (data.length === 0) {
    res.status(404).json({ code: 'NOT_FOUND', message: 'Routine not found', timestamp: new Date().toISOString() });
    return;
  }
  res.json(data);
}

export async function getExercises(req: Request, res: Response): Promise<void> {
  const workoutDayIdParam = req.query.workoutDayId;
  if (workoutDayIdParam === undefined || workoutDayIdParam === '') {
    res.status(400).json({
      code: 'BAD_REQUEST',
      message: 'workoutDayId is required',
      timestamp: new Date().toISOString(),
    });
    return;
  }
  const workoutDayId = parseInt(String(workoutDayIdParam), 10);
  if (Number.isNaN(workoutDayId)) {
    res.status(400).json({
      code: 'BAD_REQUEST',
      message: 'Invalid workoutDayId',
      timestamp: new Date().toISOString(),
    });
    return;
  }
  const muscleGroupIdParam = req.query.muscleGroupId;
  let muscleGroupId: number | undefined;
  if (muscleGroupIdParam !== undefined && muscleGroupIdParam !== '') {
    muscleGroupId = parseInt(String(muscleGroupIdParam), 10);
    if (Number.isNaN(muscleGroupId)) muscleGroupId = undefined;
  }
  const data = await readService.getExercisesByWorkoutDayId(workoutDayId, muscleGroupId);
  res.json(data);
}
