import express from 'express';
import * as readController from '../controllers/readController';
import * as reportController from '../controllers/reportController';

const router = express.Router();

router.get('/muscle-groups', readController.getMuscleGroups);
router.get('/routines', readController.getRoutines);
router.get('/routines/:routineId', readController.getRoutineById);
router.get('/routines/:routineId/days', readController.getWorkoutDays);
router.get('/routines/:routineId/weekly-report', reportController.getWeeklyReport);
router.get('/exercises', readController.getExercises);

export default router;
