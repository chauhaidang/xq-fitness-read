import { query } from '../config/database';
import type {
  WeeklyReportResponse,
  MuscleGroupTotal,
  ExerciseTotal,
  MuscleGroup,
} from '../types/dto';

function toIsoDate(val: Date | null | undefined): string | undefined {
  if (!val) return undefined;
  const d = val instanceof Date ? val : new Date(val);
  return isNaN(d.getTime()) ? undefined : d.toISOString();
}

/** Monday of the week containing date (ISO 8601). Uses UTC. */
export function calculateWeekStart(date: Date): string {
  const d = new Date(date);
  const utcDay = d.getUTCDay(); // 0 = Sunday, 1 = Monday, ...
  const daysToSubtract = utcDay === 0 ? 6 : utcDay - 1;
  d.setUTCDate(d.getUTCDate() - daysToSubtract);
  return d.toISOString().slice(0, 10);
}

export async function getWeeklyReport(
  routineId: number,
  weekStartDate?: string
): Promise<WeeklyReportResponse> {
  const existsResult = await query('SELECT 1 FROM workout_routines WHERE id = $1', [routineId]);
  if (existsResult.rows.length === 0) {
    throw new Error('Routine not found: ' + routineId);
  }

  const weekStart = weekStartDate ?? calculateWeekStart(new Date());

  const currentSnapshotData = await getSnapshotDataForWeek(routineId, weekStart);

  if (currentSnapshotData) {
    const { snapshot, snapshotExercises } = currentSnapshotData;
    const exerciseTotals = buildExerciseTotals(snapshotExercises);

    const prevDate = new Date(weekStart);
    prevDate.setUTCDate(prevDate.getUTCDate() - 7);
    const prevWeekStart = prevDate.toISOString().slice(0, 10);

    const prevSnapshotData = await getSnapshotDataForWeek(routineId, prevWeekStart);
    let prevExerciseTotals: ExerciseTotal[] = [];
    if (prevSnapshotData) {
      prevExerciseTotals = buildExerciseTotals(prevSnapshotData.snapshotExercises);
    }

    for (const currentExercise of exerciseTotals) {
      const prevExercise = prevExerciseTotals.find(
        (e) => e.exerciseName === currentExercise.exerciseName && e.muscleGroup.id === currentExercise.muscleGroup.id
      );

      if (prevExercise) {
        if (currentExercise.totalReps > prevExercise.totalReps) {
          currentExercise.progressStatusRep = 'INCREASED';
        } else if (currentExercise.totalReps < prevExercise.totalReps) {
          currentExercise.progressStatusRep = 'DECREASED';
        } else {
          currentExercise.progressStatusRep = 'MAINTAINED';
        }

        if (currentExercise.totalWeight > prevExercise.totalWeight) {
          currentExercise.progressStatusWeight = 'INCREASED';
        } else if (currentExercise.totalWeight < prevExercise.totalWeight) {
          currentExercise.progressStatusWeight = 'DECREASED';
        } else {
          currentExercise.progressStatusWeight = 'MAINTAINED';
        }
      } else {
        currentExercise.progressStatusRep = 'MAINTAINED';
        currentExercise.progressStatusWeight = 'MAINTAINED';
      }
    }

    const muscleGroupTotals =
      snapshotExercises.length === 0
        ? await aggregateSetsByMuscleGroup(routineId, weekStart)
        : await aggregateMuscleGroupTotalsFromExercises(snapshotExercises);

    return {
      routineId,
      weekStartDate: weekStart,
      hasSnapshot: true,
      snapshotCreatedAt: toIsoDate(snapshot.created_at),
      muscleGroupTotals,
      exerciseTotals,
    };
  }

  const muscleGroupTotals = await getAllMuscleGroupsWithZeroSets();
  return {
    routineId,
    weekStartDate: weekStart,
    hasSnapshot: false,
    snapshotCreatedAt: null,
    muscleGroupTotals,
    exerciseTotals: [],
  };
}

async function getSnapshotDataForWeek(routineId: number, weekStart: string) {
  const snapshotResult = await query(
    'SELECT id, created_at FROM weekly_snapshots WHERE routine_id = $1 AND week_start_date = $2',
    [routineId, weekStart]
  );

  if (snapshotResult.rows.length === 0) {
    return null;
  }

  const snapshot = snapshotResult.rows[0] as { id: number; created_at: Date };
  const snapshotWorkoutDayIds = await getSnapshotWorkoutDayIds(snapshot.id);

  let snapshotExercises: {
    exercise_name: string;
    muscle_group_id: number;
    mg_name: string;
    mg_description: string | null;
    mg_created_at: Date;
    total_reps: number;
    weight: string | number;
    total_sets: number;
  }[] = [];

  if (snapshotWorkoutDayIds.length > 0) {
    const placeholders = snapshotWorkoutDayIds.map((_, i) => `$${i + 1}`).join(',');
    const exResult = await query(
      `SELECT se.exercise_name, se.muscle_group_id, se.total_reps, se.weight, se.total_sets,
              mg.name AS mg_name, mg.description AS mg_description, mg.created_at AS mg_created_at
       FROM snapshot_exercises se
       JOIN muscle_groups mg ON mg.id = se.muscle_group_id
       WHERE se.snapshot_workout_day_id IN (${placeholders})
       ORDER BY se.id`,
      snapshotWorkoutDayIds
    );
    snapshotExercises = exResult.rows as typeof snapshotExercises;
  }

  return { snapshot, snapshotExercises };
}

async function getSnapshotWorkoutDayIds(snapshotId: number): Promise<number[]> {
  const result = await query(
    'SELECT id FROM snapshot_workout_days WHERE snapshot_id = $1',
    [snapshotId]
  );
  return (result.rows as { id: number }[]).map((r) => r.id);
}

function buildExerciseTotals(
  rows: {
    exercise_name: string;
    muscle_group_id: number;
    mg_name: string;
    mg_description: string | null;
    mg_created_at: Date;
    total_reps: number;
    weight: string | number;
    total_sets: number;
  }[]
): ExerciseTotal[] {
  const byKey = new Map<
    string,
    { name: string; muscleGroup: MuscleGroup; totalReps: number; totalWeight: number }
  >();
  for (const row of rows) {
    const key = `${row.exercise_name}|${row.muscle_group_id}`;
    const weight = typeof row.weight === 'string' ? parseFloat(row.weight) : row.weight;
    const mg: MuscleGroup = {
      id: row.muscle_group_id,
      name: row.mg_name,
      description: row.mg_description ?? undefined,
      createdAt: toIsoDate(row.mg_created_at),
    };
    const existing = byKey.get(key);
    if (existing) {
      existing.totalReps = Math.max(existing.totalReps, row.total_reps);
      existing.totalWeight = Math.max(existing.totalWeight, Number.isFinite(weight) ? weight : 0);
    } else {
      byKey.set(key, {
        name: row.exercise_name,
        muscleGroup: mg,
        totalReps: row.total_reps,
        totalWeight: Number.isFinite(weight) ? weight : 0,
      });
    }
  }
  return Array.from(byKey.values()).map((v) => ({
    exerciseName: v.name,
    muscleGroup: v.muscleGroup,
    totalReps: v.totalReps,
    totalWeight: v.totalWeight,
  }));
}

async function aggregateSetsByMuscleGroup(
  routineId: number,
  weekStartDate: string
): Promise<MuscleGroupTotal[]> {
  const result = await query(
    `SELECT mg.id AS muscle_group_id, mg.name AS muscle_group_name, mg.description AS muscle_group_description,
            mg.created_at AS muscle_group_created_at, COALESCE(SUM(swds.number_of_sets), 0)::int AS total_sets
     FROM muscle_groups mg
     LEFT JOIN snapshot_workout_day_sets swds ON mg.id = swds.muscle_group_id
     LEFT JOIN snapshot_workout_days swd ON swds.snapshot_workout_day_id = swd.id
     LEFT JOIN weekly_snapshots ws ON swd.snapshot_id = ws.id AND ws.routine_id = $1 AND ws.week_start_date = $2
     WHERE swds.id IS NULL OR ws.id IS NOT NULL
     GROUP BY mg.id, mg.name, mg.description, mg.created_at
     ORDER BY mg.name`,
    [routineId, weekStartDate]
  );
  return (result.rows as { muscle_group_id: number; muscle_group_name: string; muscle_group_description: string | null; muscle_group_created_at: Date; total_sets: number }[]).map(
    (row) => ({
      muscleGroup: {
        id: row.muscle_group_id,
        name: row.muscle_group_name,
        description: row.muscle_group_description ?? undefined,
        createdAt: toIsoDate(row.muscle_group_created_at),
      },
      totalSets: row.total_sets,
    })
  );
}

async function getAllMuscleGroupsWithZeroSets(): Promise<MuscleGroupTotal[]> {
  const result = await query(
    'SELECT id, name, description, created_at FROM muscle_groups ORDER BY name'
  );
  return (result.rows as { id: number; name: string; description: string | null; created_at: Date }[]).map(
    (row) => ({
      muscleGroup: {
        id: row.id,
        name: row.name,
        description: row.description ?? undefined,
        createdAt: toIsoDate(row.created_at),
      },
      totalSets: 0,
    })
  );
}

async function aggregateMuscleGroupTotalsFromExercises(
  rows: {
    muscle_group_id: number;
    mg_name: string;
    mg_description: string | null;
    mg_created_at: Date;
    total_sets: number;
  }[]
): Promise<MuscleGroupTotal[]> {
  const setsByMgId = new Map<number, number>();
  for (const row of rows) {
    setsByMgId.set(row.muscle_group_id, (setsByMgId.get(row.muscle_group_id) ?? 0) + row.total_sets);
  }
  const allMgResult = await query(
    'SELECT id, name, description, created_at FROM muscle_groups ORDER BY name'
  );
  return (allMgResult.rows as { id: number; name: string; description: string | null; created_at: Date }[]).map(
    (row) => ({
      muscleGroup: {
        id: row.id,
        name: row.name,
        description: row.description ?? undefined,
        createdAt: toIsoDate(row.created_at),
      },
      totalSets: setsByMgId.get(row.id) ?? 0,
    })
  );
}
