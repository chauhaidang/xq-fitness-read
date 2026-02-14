/**
 * API Client for Read Service component tests.
 * Wraps the generated xq-fitness-read-client (MuscleGroupsApi, RoutinesApi, etc.)
 * and exposes typed methods that return response data.
 */

import {
  Configuration,
  MuscleGroupsApi,
  RoutinesApi,
  WorkoutDaysApi,
  ReportsApi,
  ExercisesApi,
  type MuscleGroup,
  type WorkoutRoutine,
  type WorkoutRoutineDetail,
  type WorkoutDayDetail,
  type WeeklyReportResponse,
  type Exercise,
} from 'xq-fitness-read-client';

export class ApiClient {
  private muscleGroupsApi: MuscleGroupsApi;
  private routinesApi: RoutinesApi;
  private workoutDaysApi: WorkoutDaysApi;
  private reportsApi: ReportsApi;
  private exercisesApi: ExercisesApi;

  constructor(baseUrl: string) {
    const config = new Configuration({
      basePath: baseUrl,
    });

    this.muscleGroupsApi = new MuscleGroupsApi(config);
    this.routinesApi = new RoutinesApi(config);
    this.workoutDaysApi = new WorkoutDaysApi(config);
    this.reportsApi = new ReportsApi(config);
    this.exercisesApi = new ExercisesApi(config);
  }

  async getMuscleGroups(): Promise<MuscleGroup[]> {
    const res = await this.muscleGroupsApi.getMuscleGroups();
    return res.data;
  }

  async getRoutines(isActive?: boolean): Promise<WorkoutRoutine[]> {
    const res = await this.routinesApi.getRoutines(isActive);
    return res.data;
  }

  async getRoutineById(routineId: number): Promise<WorkoutRoutineDetail> {
    const res = await this.routinesApi.getRoutineById(routineId);
    return res.data;
  }

  async getWorkoutDays(routineId: number): Promise<WorkoutDayDetail[]> {
    const res = await this.workoutDaysApi.getWorkoutDays(routineId);
    return res.data;
  }

  async getWeeklyReport(routineId: number, weekStartDate?: string): Promise<WeeklyReportResponse> {
    const res = await this.reportsApi.getWeeklyReport(routineId, weekStartDate);
    return res.data;
  }

  async getExercises(workoutDayId: number, muscleGroupId?: number): Promise<Exercise[]> {
    const res = await this.exercisesApi.getExercises(workoutDayId, muscleGroupId);
    return res.data;
  }
}
