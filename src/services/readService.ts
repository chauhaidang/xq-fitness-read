import { query } from '../config/database';
import type {
  MuscleGroup,
  WorkoutRoutine,
  WorkoutRoutineDetail,
  WorkoutDayDetail,
  WorkoutDaySet,
  Exercise,
} from '../types/dto';

function toIsoDate(val: Date | null | undefined): string | undefined {
  if (!val) return undefined;
  const d = val instanceof Date ? val : new Date(val);
  return isNaN(d.getTime()) ? undefined : d.toISOString();
}

export async function getAllMuscleGroups(): Promise<MuscleGroup[]> {
  const result = await query(
    'SELECT id, name, description, created_at FROM muscle_groups ORDER BY name'
  );
  return (result.rows as { id: number; name: string; description: string | null; created_at: Date }[]).map(
    (row) => ({
      id: row.id,
      name: row.name,
      description: row.description ?? undefined,
      createdAt: toIsoDate(row.created_at),
    })
  );
}

export async function getAllRoutines(isActive?: boolean): Promise<WorkoutRoutine[]> {
  let sql = 'SELECT id, name, description, is_active, created_at, updated_at FROM workout_routines';
  const params: unknown[] = [];
  if (isActive !== undefined && isActive !== null) {
    sql += ' WHERE is_active = $1';
    params.push(isActive);
  }
  sql += ' ORDER BY id';
  const result = await query(sql, params.length ? params : undefined);
  return (result.rows as { id: number; name: string; description: string | null; is_active: boolean; created_at: Date; updated_at: Date }[]).map(
    (row) => ({
      id: row.id,
      name: row.name,
      description: row.description ?? undefined,
      isActive: row.is_active,
      createdAt: toIsoDate(row.created_at),
      updatedAt: toIsoDate(row.updated_at),
    })
  );
}

export async function getRoutineById(id: number): Promise<WorkoutRoutineDetail | null> {
  const routineResult = await query(
    'SELECT id, name, description, is_active, created_at, updated_at FROM workout_routines WHERE id = $1',
    [id]
  );
  if (routineResult.rows.length === 0) return null;
  const r = routineResult.rows[0] as { id: number; name: string; description: string | null; is_active: boolean; created_at: Date; updated_at: Date };
  const days = await getWorkoutDaysByRoutineId(id);
  return {
    id: r.id,
    name: r.name,
    description: r.description ?? undefined,
    isActive: r.is_active,
    createdAt: toIsoDate(r.created_at),
    updatedAt: toIsoDate(r.updated_at),
    workoutDays: days,
  };
}

export async function getWorkoutDaysByRoutineId(routineId: number): Promise<WorkoutDayDetail[]> {
  const daysResult = await query(
    `SELECT d.id, d.routine_id AS "routineId", d.day_number AS "dayNumber", d.day_name AS "dayName", d.notes, d.created_at, d.updated_at
     FROM workout_days d
     WHERE d.routine_id = $1
     ORDER BY d.day_number`,
    [routineId]
  );
  const days = daysResult.rows as {
    id: number;
    routineId: number;
    dayNumber: number;
    dayName: string;
    notes: string | null;
    created_at: Date;
    updated_at: Date;
  }[];
  const out: WorkoutDayDetail[] = [];
  for (const day of days) {
    const sets = await getSetsForWorkoutDay(day.id);
    const exercises = await getExercisesByWorkoutDayId(day.id);
    out.push({
      id: day.id,
      routineId: day.routineId,
      dayNumber: day.dayNumber,
      dayName: day.dayName,
      notes: day.notes ?? undefined,
      createdAt: toIsoDate(day.created_at),
      updatedAt: toIsoDate(day.updated_at),
      sets,
      exercises,
    });
  }
  return out;
}

async function getSetsForWorkoutDay(workoutDayId: number): Promise<WorkoutDaySet[]> {
  const result = await query(
    `SELECT s.id, s.workout_day_id AS "workoutDayId", s.number_of_sets AS "numberOfSets", s.notes, s.created_at, s.updated_at,
            mg.id AS "mg_id", mg.name AS "mg_name", mg.description AS "mg_description", mg.created_at AS "mg_created_at"
     FROM workout_day_sets s
     JOIN muscle_groups mg ON mg.id = s.muscle_group_id
     WHERE s.workout_day_id = $1
     ORDER BY s.id`,
    [workoutDayId]
  );
  return (result.rows as {
    id: number;
    workoutDayId: number;
    numberOfSets: number;
    notes: string | null;
    created_at: Date;
    updated_at: Date;
    mg_id: number;
    mg_name: string;
    mg_description: string | null;
    mg_created_at: Date;
  }[]).map((row) => ({
    id: row.id,
    workoutDayId: row.workoutDayId,
    muscleGroup: {
      id: row.mg_id,
      name: row.mg_name,
      description: row.mg_description ?? undefined,
      createdAt: toIsoDate(row.mg_created_at),
    },
    numberOfSets: row.numberOfSets,
    notes: row.notes ?? undefined,
    createdAt: toIsoDate(row.created_at),
    updatedAt: toIsoDate(row.updated_at),
  }));
}

export async function getExercisesByWorkoutDayId(
  workoutDayId: number,
  muscleGroupId?: number
): Promise<Exercise[]> {
  let sql = `SELECT e.id, e.workout_day_id AS "workoutDayId", e.muscle_group_id AS "muscleGroupId", e.exercise_name AS "exerciseName",
             e.total_reps AS "totalReps", e.weight, e.total_sets AS "totalSets", e.notes, e.created_at, e.updated_at,
             mg.id AS "mg_id", mg.name AS "mg_name", mg.description AS "mg_description", mg.created_at AS "mg_created_at"
             FROM exercises e
             JOIN muscle_groups mg ON mg.id = e.muscle_group_id
             WHERE e.workout_day_id = $1`;
  const params: unknown[] = [workoutDayId];
  if (muscleGroupId !== undefined && muscleGroupId !== null) {
    sql += ' AND e.muscle_group_id = $2';
    params.push(muscleGroupId);
  }
  sql += ' ORDER BY e.id';
  const result = await query(sql, params);
  return (result.rows as {
    id: number;
    workoutDayId: number;
    muscleGroupId: number;
    exerciseName: string;
    totalReps: number;
    weight: string | number;
    totalSets: number;
    notes: string | null;
    created_at: Date;
    updated_at: Date;
    mg_id: number;
    mg_name: string;
    mg_description: string | null;
    mg_created_at: Date;
  }[]).map((row) => ({
    id: row.id,
    workoutDayId: row.workoutDayId,
    muscleGroupId: row.muscleGroupId,
    exerciseName: row.exerciseName,
    muscleGroup: {
      id: row.mg_id,
      name: row.mg_name,
      description: row.mg_description ?? undefined,
      createdAt: toIsoDate(row.mg_created_at),
    },
    totalReps: row.totalReps,
    weight: typeof row.weight === 'string' ? parseFloat(row.weight) : row.weight,
    totalSets: row.totalSets,
    notes: row.notes ?? undefined,
    createdAt: toIsoDate(row.created_at),
    updatedAt: toIsoDate(row.updated_at),
  }));
}
