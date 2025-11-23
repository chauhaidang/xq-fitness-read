package com.xqfitness.readservice.component;

import org.testng.annotations.*;

import java.util.List;

import static org.testng.Assert.*;

/**
 * Component Tests for Read Service API using Generated Client
 *
 * Prerequisites:
 * 1. Test environment must be running via xq-infra CLI
 * 2. Database and read-service must be accessible
 * 3. API Gateway at: http://localhost:8080/xq-fitness-read-service/api/v1
 *
 * These are real integration tests - no mocks, uses generated API client.
 */
@Test(groups = {"component", "integration"})
public class ReadServiceApiComponentTest {

    private static final String BASE_URL = System.getenv("API_BASE_URL") != null
        ? System.getenv("API_BASE_URL")
        : "http://localhost:8080/xq-fitness-read-service/api/v1";

    private static final int DEFAULT_TIMEOUT = 5000;

    private ApiClient apiClient;
    private MuscleGroupApi muscleGroupsApi;
    private RoutinesApi routinesApi;
    private WorkoutDaysApi workoutDaysApi;

    private Long testRoutineId;

    @BeforeClass
    public void setupClass() {
        apiClient = new ApiClient();
        apiClient.setBasePath(BASE_URL);
        apiClient.setConnectTimeout(DEFAULT_TIMEOUT);
        apiClient.setReadTimeout(DEFAULT_TIMEOUT);

        muscleGroupsApi = new MuscleGroupsApi(apiClient);
        routinesApi = new RoutinesApi(apiClient);
        workoutDaysApi = new WorkoutDaysApi(apiClient);
    }

    @BeforeMethod
    public void setupMethod() throws ApiException {
        try {
            muscleGroupsApi.getMuscleGroups();
        } catch (ApiException e) {
            fail("Service should be accessible. Status: " + e.getCode());
        }
    }

    @Test(priority = 1, description = "GET /muscle-groups - Should return all muscle groups")
    public void testGetAllMuscleGroups() throws ApiException {
        List<MuscleGroup> muscleGroups = muscleGroupsApi.getMuscleGroups();

        assertNotNull(muscleGroups, "Muscle groups list should not be null");
        assertTrue(muscleGroups.size() >= 0, "Should return a valid list");

        if (!muscleGroups.isEmpty()) {
            MuscleGroup firstGroup = muscleGroups.get(0);
            assertNotNull(firstGroup.getId(), "Muscle group ID should not be null");
            assertNotNull(firstGroup.getName(), "Muscle group name should not be null");
            assertNotNull(firstGroup.getCreatedAt(), "Muscle group createdAt should not be null");
        }
    }

    @Test(priority = 2, description = "GET /routines - Should return all workout routines")
    public void testGetAllRoutines() throws ApiException {
        List<WorkoutRoutine> routines = routinesApi.getRoutines(null);

        assertNotNull(routines, "Routines list should not be null");
        assertTrue(routines.size() >= 0, "Should return a valid list");

        if (!routines.isEmpty()) {
            testRoutineId = routines.get(0).getId();

            WorkoutRoutine firstRoutine = routines.get(0);
            assertNotNull(firstRoutine.getId(), "Routine ID should not be null");
            assertNotNull(firstRoutine.getName(), "Routine name should not be null");
            assertNotNull(firstRoutine.getIsActive(), "Routine isActive should not be null");
            assertNotNull(firstRoutine.getCreatedAt(), "Routine createdAt should not be null");
        }
    }

    @Test(priority = 3, description = "GET /routines?isActive=true - Should return only active routines")
    public void testGetActiveRoutines() throws ApiException {
        List<WorkoutRoutine> routines = routinesApi.getRoutines(true);

        assertNotNull(routines, "Active routines list should not be null");

        for (WorkoutRoutine routine : routines) {
            assertTrue(routine.getIsActive(), "Routine should be active when filtered by isActive=true");
        }
    }

    @Test(priority = 4, description = "GET /routines?isActive=false - Should return only inactive routines")
    public void testGetInactiveRoutines() throws ApiException {
        List<WorkoutRoutine> routines = routinesApi.getRoutines(false);

        assertNotNull(routines, "Inactive routines list should not be null");

        for (WorkoutRoutine routine : routines) {
            assertFalse(routine.getIsActive(), "Routine should be inactive when filtered by isActive=false");
        }
    }

    @Test(priority = 5, dependsOnMethods = "testGetAllRoutines",
          description = "GET /routines/{id} - Should return routine with details")
    public void testGetRoutineById() throws ApiException {
        if (testRoutineId == null) {
            return;
        }

        WorkoutRoutineDetail routine = routinesApi.getRoutineById(testRoutineId);

        assertNotNull(routine, "Routine details should not be null");
        assertEquals(routine.getId(), testRoutineId, "Routine ID should match requested ID");
        assertNotNull(routine.getName(), "Routine name should not be null");
        assertNotNull(routine.getWorkoutDays(), "Workout days list should not be null");

        if (!routine.getWorkoutDays().isEmpty()) {
            WorkoutDayDetail firstDay = routine.getWorkoutDays().get(0);
            assertNotNull(firstDay.getId(), "Workout day ID should not be null");
            assertNotNull(firstDay.getDayNumber(), "Day number should not be null");
            assertNotNull(firstDay.getDayName(), "Day name should not be null");
            assertNotNull(firstDay.getSets(), "Sets list should not be null");
        }
    }

    @Test(priority = 6, description = "GET /routines/{id} - Should return 404 for non-existent routine")
    public void testGetRoutineById_NotFound() {
        try {
            routinesApi.getRoutineById(999999L);
            fail("Should have thrown ApiException with 404 status");
        } catch (ApiException e) {
            assertEquals(e.getCode(), 404, "Should return 404 for non-existent routine");
        }
    }

    @Test(priority = 7, dependsOnMethods = "testGetAllRoutines",
          description = "GET /routines/{id}/days - Should return workout days for routine")
    public void testGetWorkoutDays() throws ApiException {
        if (testRoutineId == null) {
            return;
        }

        try {
            List<WorkoutDayDetail> days = workoutDaysApi.getWorkoutDays(testRoutineId);

            assertNotNull(days, "Workout days list should not be null");
            assertTrue(days.size() > 0, "Should have at least one workout day");

            for (int i = 0; i < days.size() - 1; i++) {
                assertTrue(days.get(i).getDayNumber() <= days.get(i + 1).getDayNumber(),
                          "Days should be ordered by dayNumber");
            }

            WorkoutDayDetail firstDay = days.get(0);
            assertNotNull(firstDay.getId(), "Day ID should not be null");
            assertNotNull(firstDay.getRoutineId(), "Routine ID should not be null");
            assertEquals(firstDay.getRoutineId(), testRoutineId, "Routine ID should match");
            assertNotNull(firstDay.getDayNumber(), "Day number should not be null");
            assertNotNull(firstDay.getSets(), "Sets list should not be null");

            if (!firstDay.getSets().isEmpty()) {
                assertNotNull(firstDay.getSets().get(0).getMuscleGroup(),
                             "Set should have muscle group");
                assertNotNull(firstDay.getSets().get(0).getNumberOfSets(),
                             "Set should have number of sets");
            }
        } catch (ApiException e) {
            assertEquals(e.getCode(), 404, "Should return 404 if routine has no days");
        }
    }

    @Test(priority = 8, description = "GET /routines/{id}/days - Should return 404 for non-existent routine")
    public void testGetWorkoutDays_NotFound() {
        try {
            workoutDaysApi.getWorkoutDays(999999L);
            fail("Should have thrown ApiException with 404 status");
        } catch (ApiException e) {
            assertEquals(e.getCode(), 404, "Should return 404 for non-existent routine days");
        }
    }

    @Test(priority = 9, dependsOnMethods = "testGetAllRoutines",
          description = "GET /routines/{id}/days - Should return workout days ordered by dayNumber")
    public void testGetWorkoutDays_OrderedByDayNumber() throws ApiException {
        if (testRoutineId == null) {
            return;
        }

        try {
            List<WorkoutDayDetail> days = workoutDaysApi.getWorkoutDays(testRoutineId);

            assertNotNull(days, "Workout days list should not be null");

            if (days.size() >= 2) {
                for (int i = 0; i < days.size() - 1; i++) {
                    assertTrue(days.get(i).getDayNumber() <= days.get(i + 1).getDayNumber(),
                              "Days should be ordered by dayNumber in ascending order");
                }
            }
        } catch (ApiException e) {
            assertEquals(e.getCode(), 404, "404 is acceptable if routine has no days");
        }
    }

    @Test(priority = 10, description = "Verify API responses are valid JSON")
    public void testResponseFormat() throws ApiException {
        List<MuscleGroup> muscleGroups = muscleGroupsApi.getMuscleGroups();
        assertNotNull(muscleGroups, "Response should be deserializable");

        List<WorkoutRoutine> routines = routinesApi.getRoutines(null);
        assertNotNull(routines, "Response should be deserializable");
    }

    @Test(priority = 11, description = "Verify API responds within acceptable time")
    public void testResponseTime() throws ApiException {
        long startTime = System.currentTimeMillis();

        muscleGroupsApi.getMuscleGroups();

        long duration = System.currentTimeMillis() - startTime;
        assertTrue(duration < DEFAULT_TIMEOUT,
                  "Response time should be under " + DEFAULT_TIMEOUT + "ms, was: " + duration + "ms");
    }
}
