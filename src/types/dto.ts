/**
 * DTOs matching read-service-api.yaml (OpenAPI) and Java DTOs.
 * Date fields as ISO 8601 strings.
 */

export interface MuscleGroup {
  id: number;
  name: string;
  description?: string;
  createdAt?: string;
}

export interface WorkoutRoutine {
  id: number;
  name: string;
  description?: string;
  isActive: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface WorkoutDaySet {
  id: number;
  workoutDayId: number;
  muscleGroup: MuscleGroup;
  numberOfSets: number;
  notes?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface Exercise {
  id: number;
  workoutDayId: number;
  muscleGroupId: number;
  exerciseName: string;
  muscleGroup: MuscleGroup;
  totalReps: number;
  weight: number;
  totalSets: number;
  notes?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface WorkoutDayDetail {
  id: number;
  routineId: number;
  dayNumber: number;
  dayName: string;
  notes?: string;
  createdAt?: string;
  updatedAt?: string;
  sets: WorkoutDaySet[];
  exercises: Exercise[];
}

export interface WorkoutRoutineDetail extends WorkoutRoutine {
  workoutDays: WorkoutDayDetail[];
}

export interface MuscleGroupTotal {
  muscleGroup: MuscleGroup;
  totalSets: number;
}

export interface ExerciseTotal {
  exerciseName: string;
  muscleGroup: MuscleGroup;
  totalReps: number;
  totalWeight: number;
  progressStatusRep?: 'INCREASED' | 'DECREASED' | 'MAINTAINED' | null;
  progressStatusWeight?: 'INCREASED' | 'DECREASED' | 'MAINTAINED' | null;
}

export interface WeeklyReportResponse {
  routineId: number;
  weekStartDate: string; // YYYY-MM-DD
  hasSnapshot: boolean;
  snapshotCreatedAt?: string | null;
  muscleGroupTotals: MuscleGroupTotal[];
  exerciseTotals: ExerciseTotal[];
}

export interface ErrorResponse {
  code: string;
  message: string;
  timestamp?: string;
}
