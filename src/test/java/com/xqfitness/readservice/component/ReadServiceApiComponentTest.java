package com.xqfitness.readservice.component;

import com.xqfitness.client.read_service.invoker.*;
import com.xqfitness.client.read_service.model.*;
import com.google.gson.reflect.TypeToken;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.filter.log.LogDetail;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.response.Response;
import org.testng.annotations.*;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.xqfitness.client.read_service.invoker.GsonObjectMapper.gson;
import static org.testng.Assert.*;

/**
 * Component tests for Read Service API using generated Rest Assured client.
 * Each test uses DbTestFixture to create data in isolation, calls the API, and cleans up.
 *
 * Prerequisites:
 * 1. Test environment must be running via xq-infra CLI (from service directory: xq-infra generate -f ./test-env && xq-infra up)
 * 2. Database and read-service must be accessible
 * 3. API_BASE_URL defaults to http://localhost:8080/xq-fitness-read-service/api/v1 (gateway location /xq-fitness-read-service/ from nginx-gateway.conf)
 * 4. DB connection uses DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD (defaults: localhost, 5432, xq_fitness, xq_user, xq_password)
 */
@Test(groups = { "component", "integration" })
public class ReadServiceApiComponentTest {

    private static final String BASE_URL = System.getenv("API_BASE_URL") != null
            ? System.getenv("API_BASE_URL")
            : "http://localhost:8080/xq-fitness-read-service/api/v1";

    private ApiClient apiClient;

    /** Routine IDs created in the current test method; cleaned up in @AfterMethod. */
    private final List<Long> routineIdsToCleanUp = new ArrayList<>();

    /** DB connection for the current test method; opened in @BeforeMethod, closed in @AfterMethod. */
    private Connection dbConnection;

    @BeforeClass
    public void setupClass() {
        ApiClient.Config config = ApiClient.Config.apiConfig();
        config.reqSpecSupplier(() -> new RequestSpecBuilder()
                .setBaseUri(BASE_URL)
                .setConfig(RestAssuredConfig.config().objectMapperConfig(
                        ObjectMapperConfig.objectMapperConfig().defaultObjectMapper(gson())))
                .addFilter(new RequestLoggingFilter(LogDetail.ALL))
                .addFilter(new ResponseLoggingFilter(LogDetail.ALL)));
        apiClient = ApiClient.api(config);
    }

    /** Retry when backend is not ready yet (e.g. container still starting). */
    private static final int REACHABILITY_RETRIES = 5;
    private static final long REACHABILITY_DELAY_MS = 3_000;

    @BeforeMethod
    public void setupMethod() {
        // Verify service is reachable; retry when backend is not ready yet
        Exception lastException = null;
        boolean reachable = false;
        for (int attempt = 1; attempt <= REACHABILITY_RETRIES; attempt++) {
            try {
                List<MuscleGroup> muscleGroups = apiClient.muscleGroups().getMuscleGroups().executeAs(r -> r);
                assertNotNull(muscleGroups, "Service should be accessible");
                reachable = true;
                break;
            } catch (Exception e) {
                lastException = e;
                if (attempt < REACHABILITY_RETRIES) {
                    try {
                        Thread.sleep(REACHABILITY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError("Interrupted while waiting for service", ie);
                    }
                }
            }
        }
        if (!reachable) {
            fail("Service not reachable after " + REACHABILITY_RETRIES + " attempts: " + (lastException != null ? lastException.getMessage() : "unknown"));
        }
        routineIdsToCleanUp.clear();
        try {
            dbConnection = DbTestFixture.getConnection();
        } catch (SQLException e) {
            throw new AssertionError("Could not get DB connection for test", e);
        }
    }

    @AfterMethod
    public void tearDownMethod() {
        for (Long routineId : routineIdsToCleanUp) {
            if (routineId == null) continue;
            try (Connection conn = DbTestFixture.getConnection()) {
                DbTestFixture.deleteRoutine(conn, routineId);
            } catch (SQLException e) {
                System.err.println("[ReadServiceApiComponentTest] Failed to delete routine " + routineId + ": " + e.getMessage());
            }
        }
        routineIdsToCleanUp.clear();
        if (dbConnection != null) {
            try {
                dbConnection.close();
            } catch (SQLException e) {
                System.err.println("[ReadServiceApiComponentTest] Failed to close DB connection: " + e.getMessage());
            }
            dbConnection = null;
        }
    }

    /** Register a routine ID for cleanup in @AfterMethod. Call after creating data via DbTestFixture. */
    private void registerRoutineForCleanup(Long routineId) {
        if (routineId != null) {
            routineIdsToCleanUp.add(routineId);
        }
    }

    @Test(description = "GET /muscle-groups - Should return 200 and array of muscle groups")
    public void testGetAllMuscleGroups() {
        List<MuscleGroup> muscleGroups = apiClient.muscleGroups().getMuscleGroups().executeAs(r -> r);

        assertNotNull(muscleGroups, "Muscle groups list should not be null");
        assertTrue(muscleGroups.size() >= 0, "Should return a valid list");
        if (!muscleGroups.isEmpty()) {
            MuscleGroup first = muscleGroups.get(0);
            assertNotNull(first.getId(), "Muscle group ID should not be null");
            assertNotNull(first.getName(), "Muscle group name should not be null");
            assertNotNull(first.getCreatedAt(), "Muscle group createdAt should not be null");
        }
    }

    @Test(description = "GET /routines - Should return routine created via DB")
    public void testGetRoutines_ReturnsDbSeededRoutine() throws Exception {
        Long routineId = DbTestFixture.createRoutine(dbConnection, "Component Test Routine", "Description", true);
        assertNotNull(routineId, "Routine should be created");
        registerRoutineForCleanup(routineId);

        List<WorkoutRoutine> routines = apiClient.routines().getRoutines().executeAs(r -> r);
        assertNotNull(routines, "Routines list should not be null");
        final Long id = routineId;
        WorkoutRoutine found = routines.stream().filter(r -> r.getId().equals(id)).findFirst().orElse(null);
        assertNotNull(found, "Created routine " + id + " should appear in list. Returned IDs: " + routines.stream().map(WorkoutRoutine::getId).toList() + ". Ensure service uses same DB as test (DB_HOST/DB_PORT).");
        assertEquals(found.getName(), "Component Test Routine", "Routine name should match");
        assertTrue(found.getIsActive(), "Routine should be active");
    }

    @Test(description = "GET /routines?isActive=true - Should return only active routines")
    public void testGetRoutines_ActiveFilter() throws Exception {
        Long activeId = DbTestFixture.createRoutine(dbConnection, "Active Routine", null, true);
        Long inactiveId = DbTestFixture.createRoutine(dbConnection, "Inactive Routine", null, false);
        assertNotNull(activeId);
        assertNotNull(inactiveId);
        registerRoutineForCleanup(activeId);
        registerRoutineForCleanup(inactiveId);

        List<WorkoutRoutine> activeRoutines = apiClient.routines().getRoutines().isActiveQuery(true).executeAs(r -> r);
            assertNotNull(activeRoutines);
            for (WorkoutRoutine r : activeRoutines) {
                assertTrue(r.getIsActive(), "Filter isActive=true should return only active");
            }
            final Long activeIdFinal = activeId;
            assertTrue(activeRoutines.stream().anyMatch(r -> r.getId().equals(activeIdFinal)), "Active routine " + activeIdFinal + " should be in list. Returned IDs: " + activeRoutines.stream().map(WorkoutRoutine::getId).toList());

            List<WorkoutRoutine> inactiveRoutines = apiClient.routines().getRoutines().isActiveQuery(false).executeAs(r -> r);
            assertNotNull(inactiveRoutines);
            for (WorkoutRoutine r : inactiveRoutines) {
                assertFalse(r.getIsActive(), "Filter isActive=false should return only inactive");
            }
            final Long inactiveIdFinal = inactiveId;
            assertTrue(inactiveRoutines.stream().anyMatch(r -> r.getId().equals(inactiveIdFinal)), "Inactive routine " + inactiveIdFinal + " should be in list. Returned IDs: " + inactiveRoutines.stream().map(WorkoutRoutine::getId).toList());
    }

    @Test(description = "GET /routines/{id} - Should return routine with days and sets from DB")
    public void testGetRoutineById_ReturnsDetail() throws Exception {
        Long routineId = DbTestFixture.createRoutine(dbConnection, "Detail Test Routine", null, true);
        registerRoutineForCleanup(routineId);
        Long dayId = DbTestFixture.createWorkoutDay(dbConnection, routineId, 1, "Push Day", null);
        assertNotNull(dayId);
        Long setId = DbTestFixture.createWorkoutDaySet(dbConnection, dayId, 1, 3, null);
        assertNotNull(setId);

        Response response = apiClient.routines().getRoutineById().routineIdPath(routineId).execute(r -> r);
        assertEquals(response.getStatusCode(), 200, "GET /routines/{id} - status: " + response.getStatusCode() + ", body: " + response.getBody().asString());
        WorkoutRoutineDetail detail = response.as(WorkoutRoutineDetail.class);
        assertNotNull(detail, "Routine detail should not be null");
        assertEquals(detail.getId(), routineId, "Routine ID should match");
        assertEquals(detail.getName(), "Detail Test Routine", "Name should match");
        assertNotNull(detail.getWorkoutDays(), "Workout days should not be null");
        assertEquals(detail.getWorkoutDays().size(), 1, "Should have one day");
        WorkoutDayDetail day = detail.getWorkoutDays().get(0);
        assertEquals(day.getDayNumber(), Integer.valueOf(1), "Day number should match");
        assertEquals(day.getDayName(), "Push Day", "Day name should match");
        assertNotNull(day.getSets(), "Sets should not be null");
        assertEquals(day.getSets().size(), 1, "Should have one set");
        assertEquals(day.getSets().get(0).getNumberOfSets(), Integer.valueOf(3), "Number of sets should match");
    }

    @Test(description = "GET /routines/{id} - Should return 404 for non-existent routine")
    public void testGetRoutineById_NotFound() {
        Response response = apiClient.routines().getRoutineById().routineIdPath(999999L).execute(r -> r);
        assertEquals(response.getStatusCode(), 404, "Should return 404 for non-existent routine");
    }

    @Test(description = "GET /routines/{id}/days - Should return days ordered by dayNumber")
    public void testGetWorkoutDays_OrderedByDayNumber() throws Exception {
        Long routineId = DbTestFixture.createRoutine(dbConnection, "Days Test Routine", null, true);
        registerRoutineForCleanup(routineId);
        DbTestFixture.createWorkoutDay(dbConnection, routineId, 2, "Day Two", null);
        DbTestFixture.createWorkoutDay(dbConnection, routineId, 1, "Day One", null);

        Response response = apiClient.workoutDays().getWorkoutDays().routineIdPath(routineId).execute(r -> r);
        assertEquals(response.getStatusCode(), 200, "GET /routines/{id}/days - status: " + response.getStatusCode() + ", body: " + response.getBody().asString());
        Type listType = new TypeToken<List<WorkoutDayDetail>>(){}.getType();
        List<WorkoutDayDetail> days = response.as(listType);
        assertNotNull(days, "Days list should not be null");
        assertEquals(days.size(), 2, "Should have two days");
        assertTrue(days.get(0).getDayNumber() <= days.get(1).getDayNumber(), "Days should be ordered by dayNumber");
        assertEquals(days.get(0).getDayName(), "Day One", "First day should be Day One");
        assertEquals(days.get(1).getDayName(), "Day Two", "Second day should be Day Two");
    }

    @Test(description = "GET /routines/{id}/days - Should return 404 for non-existent routine")
    public void testGetWorkoutDays_NotFound() {
        Response response = apiClient.workoutDays().getWorkoutDays().routineIdPath(999999L).execute(r -> r);
        assertEquals(response.getStatusCode(), 404, "Should return 404 for non-existent routine");
    }

    @Test(description = "GET /routines/{id}/weekly-report - Should return empty report when no snapshot")
    public void testGetWeeklyReport_NoSnapshot() throws Exception {
        Long routineId = DbTestFixture.createRoutine(dbConnection, "Report No Snapshot Routine", null, true);
        registerRoutineForCleanup(routineId);

        Response response = apiClient.reports().getWeeklyReport().routineIdPath(routineId).execute(r -> r);
        assertEquals(response.getStatusCode(), 200, "GET /routines/{id}/weekly-report (no snapshot) - status: " + response.getStatusCode() + ", body: " + response.getBody().asString());
        WeeklyReportResponse report = response.as(WeeklyReportResponse.class);
        assertNotNull(report, "Report should not be null");
        assertEquals(report.getRoutineId(), routineId, "Routine ID should match");
        assertFalse(report.getHasSnapshot(), "Has snapshot should be false");
        assertNull(report.getSnapshotCreatedAt(), "Snapshot created at should be null");
        assertNotNull(report.getMuscleGroupTotals(), "Muscle group totals should not be null");
        assertNotNull(report.getExerciseTotals(), "Exercise totals should not be null");
        for (MuscleGroupTotal total : report.getMuscleGroupTotals()) {
            assertEquals(total.getTotalSets().intValue(), 0, "Total sets should be zero when no snapshot");
        }
    }

    @Test(description = "GET /routines/{id}/weekly-report - Should return report with snapshot totals")
    public void testGetWeeklyReport_WithSnapshot() throws Exception {
        LocalDate weekStart = DbTestFixture.getCurrentWeekStart();
        Long routineId = DbTestFixture.createRoutine(dbConnection, "Report Snapshot Routine", null, true);
        registerRoutineForCleanup(routineId);
        Long dayId = DbTestFixture.createWorkoutDay(dbConnection, routineId, 1, "Push Day", null);
        Long setId = DbTestFixture.createWorkoutDaySet(dbConnection, dayId, 1, 5, null); // Chest = 1, 5 sets
        Long snapshotId = DbTestFixture.createSnapshot(dbConnection, routineId, weekStart);
        Long snapshotDayId = DbTestFixture.createSnapshotWorkoutDay(dbConnection, snapshotId, dayId, 1, "Push Day", null);
        DbTestFixture.createSnapshotWorkoutDaySet(dbConnection, snapshotDayId, setId, 1, 5, null);

        Response response = apiClient.reports().getWeeklyReport().routineIdPath(routineId).execute(r -> r);
        assertEquals(response.getStatusCode(), 200, "GET /routines/{id}/weekly-report (with snapshot) - status: " + response.getStatusCode() + ", body: " + response.getBody().asString());
        WeeklyReportResponse report = response.as(WeeklyReportResponse.class);
        assertNotNull(report, "Report should not be null");
        assertEquals(report.getRoutineId(), routineId, "Routine ID should match");
        assertTrue(report.getHasSnapshot(), "Has snapshot should be true");
        assertNotNull(report.getSnapshotCreatedAt(), "Snapshot created at should be set");
        assertNotNull(report.getMuscleGroupTotals(), "Muscle group totals should not be null");
        MuscleGroupTotal chestTotal = report.getMuscleGroupTotals().stream()
                .filter(t -> t.getMuscleGroup() != null && t.getMuscleGroup().getId() != null && t.getMuscleGroup().getId() == 1L)
                .findFirst().orElse(null);
        assertNotNull(chestTotal, "Chest muscle group should be in report");
        assertEquals(chestTotal.getTotalSets().intValue(), 5, "Chest total sets should be 5");
    }

    @Test(description = "GET /routines/{id}/weekly-report - Should return exerciseTotals and muscleGroupTotals from snapshot_exercises (T023 US2)")
    public void testGetWeeklyReport_WithSnapshotExercises_ReturnsExerciseTotalsAndMuscleGroupTotals() throws Exception {
        LocalDate weekStart = DbTestFixture.getCurrentWeekStart();
        Long routineId = DbTestFixture.createRoutine(dbConnection, "Report Exercise Totals Routine", null, true);
        registerRoutineForCleanup(routineId);
        Long dayId = DbTestFixture.createWorkoutDay(dbConnection, routineId, 1, "Push Day", null);
        Long snapshotId = DbTestFixture.createSnapshot(dbConnection, routineId, weekStart);
        Long snapshotDayId = DbTestFixture.createSnapshotWorkoutDay(dbConnection, snapshotId, dayId, 1, "Push Day", null);
        // Snapshot exercise: Bench Press, Chest (muscle_group_id=1), 30 reps, 135 weight, 3 sets
        Long exId = DbTestFixture.createSnapshotExercise(dbConnection, snapshotDayId, 1, "Bench Press", 1, 30, 135.0, 3, null);
        assertNotNull(exId, "Snapshot exercise should be created");

        Response response = apiClient.reports().getWeeklyReport().routineIdPath(routineId).execute(r -> r);
        assertEquals(response.getStatusCode(), 200, "GET /routines/{id}/weekly-report (snapshot_exercises) - status: " + response.getStatusCode() + ", body: " + response.getBody().asString());
        WeeklyReportResponse report = response.as(WeeklyReportResponse.class);
        assertNotNull(report, "Report should not be null");
        assertEquals(report.getRoutineId(), routineId, "Routine ID should match");
        assertTrue(report.getHasSnapshot(), "Has snapshot should be true");
        assertNotNull(report.getExerciseTotals(), "Exercise totals should not be null");
        assertFalse(report.getExerciseTotals().isEmpty(), "Exercise totals should contain entries from snapshot_exercises");
        // Find Bench Press in exercise totals
        ExerciseTotal benchTotal = report.getExerciseTotals().stream()
                .filter(e -> "Bench Press".equals(e.getExerciseName()))
                .findFirst()
                .orElse(null);
        assertNotNull(benchTotal, "Exercise total for Bench Press should be present. exerciseTotals: " + report.getExerciseTotals());
        assertEquals(benchTotal.getTotalReps().intValue(), 30, "Bench Press totalReps should be 30");
        assertEquals(benchTotal.getTotalWeight().doubleValue(), 135.0, 0.01, "Bench Press totalWeight should be 135");
        assertNotNull(benchTotal.getMuscleGroup(), "Exercise total should have muscleGroup");
        assertEquals(benchTotal.getMuscleGroup().getId(), Long.valueOf(1L), "Bench Press muscle group should be Chest (id=1)");
        // Muscle group totals: Chest should have totalSets = 3 from snapshot_exercises
        assertNotNull(report.getMuscleGroupTotals(), "Muscle group totals should not be null");
        MuscleGroupTotal chestTotal = report.getMuscleGroupTotals().stream()
                .filter(t -> t.getMuscleGroup() != null && t.getMuscleGroup().getId() != null && t.getMuscleGroup().getId() == 1L)
                .findFirst().orElse(null);
        assertNotNull(chestTotal, "Chest muscle group should be in report");
        assertEquals(chestTotal.getTotalSets().intValue(), 3, "Chest totalSets should be 3 (from snapshot_exercises total_sets)");
    }

    @Test(description = "GET /routines/{id}/weekly-report - Multiple exercises in one muscle group (Chest): exerciseTotals has both, muscleGroupTotals sums totalSets")
    public void testGetWeeklyReport_WithSnapshotExercises_MultipleExercisesInOneMuscleGroup() throws Exception {
        LocalDate weekStart = DbTestFixture.getCurrentWeekStart();
        Long routineId = DbTestFixture.createRoutine(dbConnection, "Multi-Exercise Chest Routine", null, true);
        registerRoutineForCleanup(routineId);
        Long dayId = DbTestFixture.createWorkoutDay(dbConnection, routineId, 1, "Push Day", null);
        Long snapshotId = DbTestFixture.createSnapshot(dbConnection, routineId, weekStart);
        Long snapshotDayId = DbTestFixture.createSnapshotWorkoutDay(dbConnection, snapshotId, dayId, 1, "Push Day", null);
        // Chest (muscle_group_id=1): two exercises
        DbTestFixture.createSnapshotExercise(dbConnection, snapshotDayId, 1, "Bench Press", 1, 30, 135.0, 3, null);
        DbTestFixture.createSnapshotExercise(dbConnection, snapshotDayId, 2, "Dumbbell Flyes", 1, 20, 25.0, 2, null);

        Response response = apiClient.reports().getWeeklyReport().routineIdPath(routineId).execute(r -> r);
        assertEquals(response.getStatusCode(), 200, "GET /routines/{id}/weekly-report (multi-exercise) - status: " + response.getStatusCode() + ", body: " + response.getBody().asString());
        WeeklyReportResponse report = response.as(WeeklyReportResponse.class);
        assertNotNull(report);
        assertTrue(report.getHasSnapshot());
        assertNotNull(report.getExerciseTotals());
        assertEquals(report.getExerciseTotals().size(), 2, "Should have two exercise totals (Bench Press + Dumbbell Flyes)");

        ExerciseTotal benchPress = report.getExerciseTotals().stream().filter(e -> "Bench Press".equals(e.getExerciseName())).findFirst().orElse(null);
        ExerciseTotal dumbbellFlyes = report.getExerciseTotals().stream().filter(e -> "Dumbbell Flyes".equals(e.getExerciseName())).findFirst().orElse(null);
        assertNotNull(benchPress, "Bench Press should be in exerciseTotals");
        assertNotNull(dumbbellFlyes, "Dumbbell Flyes should be in exerciseTotals");
        assertEquals(benchPress.getTotalReps().intValue(), 30);
        assertEquals(benchPress.getTotalWeight().doubleValue(), 135.0, 0.01);
        assertEquals(benchPress.getMuscleGroup().getId(), Long.valueOf(1L));
        assertEquals(dumbbellFlyes.getTotalReps().intValue(), 20);
        assertEquals(dumbbellFlyes.getTotalWeight().doubleValue(), 25.0, 0.01);
        assertEquals(dumbbellFlyes.getMuscleGroup().getId(), Long.valueOf(1L));

        // Chest totalSets = 3 + 2 = 5
        MuscleGroupTotal chestTotal = report.getMuscleGroupTotals().stream()
                .filter(t -> t.getMuscleGroup() != null && t.getMuscleGroup().getId() == 1L)
                .findFirst().orElse(null);
        assertNotNull(chestTotal);
        assertEquals(chestTotal.getTotalSets().intValue(), 5, "Chest totalSets should be 5 (3 + 2 from both exercises)");
    }

    @Test(description = "GET /routines/{id}/weekly-report - Multiple muscle groups in one workout day: exerciseTotals per group, muscleGroupTotals per group")
    public void testGetWeeklyReport_WithSnapshotExercises_MultipleMuscleGroupsInWorkoutDay() throws Exception {
        LocalDate weekStart = DbTestFixture.getCurrentWeekStart();
        Long routineId = DbTestFixture.createRoutine(dbConnection, "Multi-Muscle Day Routine", null, true);
        registerRoutineForCleanup(routineId);
        Long dayId = DbTestFixture.createWorkoutDay(dbConnection, routineId, 1, "Push Day", null);
        Long snapshotId = DbTestFixture.createSnapshot(dbConnection, routineId, weekStart);
        Long snapshotDayId = DbTestFixture.createSnapshotWorkoutDay(dbConnection, snapshotId, dayId, 1, "Push Day", null);
        // Chest (1): Bench Press 3 sets
        DbTestFixture.createSnapshotExercise(dbConnection, snapshotDayId, 1, "Bench Press", 1, 30, 135.0, 3, null);
        // Back (2): Barbell Row 4 sets
        DbTestFixture.createSnapshotExercise(dbConnection, snapshotDayId, 2, "Barbell Row", 2, 24, 95.0, 4, null);

        Response response = apiClient.reports().getWeeklyReport().routineIdPath(routineId).execute(r -> r);
        assertEquals(response.getStatusCode(), 200, "GET /routines/{id}/weekly-report (multi-muscle) - status: " + response.getStatusCode() + ", body: " + response.getBody().asString());
        WeeklyReportResponse report = response.as(WeeklyReportResponse.class);
        assertNotNull(report);
        assertTrue(report.getHasSnapshot());
        assertNotNull(report.getExerciseTotals());
        assertEquals(report.getExerciseTotals().size(), 2, "Should have two exercise totals (Chest + Back)");

        ExerciseTotal benchPress = report.getExerciseTotals().stream().filter(e -> "Bench Press".equals(e.getExerciseName())).findFirst().orElse(null);
        ExerciseTotal barbellRow = report.getExerciseTotals().stream().filter(e -> "Barbell Row".equals(e.getExerciseName())).findFirst().orElse(null);
        assertNotNull(benchPress);
        assertNotNull(barbellRow);
        assertEquals(benchPress.getMuscleGroup().getId(), Long.valueOf(1L), "Bench Press is Chest");
        assertEquals(barbellRow.getMuscleGroup().getId(), Long.valueOf(2L), "Barbell Row is Back");

        MuscleGroupTotal chestTotal = report.getMuscleGroupTotals().stream()
                .filter(t -> t.getMuscleGroup() != null && t.getMuscleGroup().getId() == 1L)
                .findFirst().orElse(null);
        MuscleGroupTotal backTotal = report.getMuscleGroupTotals().stream()
                .filter(t -> t.getMuscleGroup() != null && t.getMuscleGroup().getId() == 2L)
                .findFirst().orElse(null);
        assertNotNull(chestTotal);
        assertNotNull(backTotal);
        assertEquals(chestTotal.getTotalSets().intValue(), 3, "Chest totalSets should be 3");
        assertEquals(backTotal.getTotalSets().intValue(), 4, "Back totalSets should be 4");
    }

    @Test(description = "GET /routines/{id}/weekly-report - Two workout days, same muscle group (Chest), different exercises: exerciseTotals from both days, muscleGroupTotals sums across days")
    public void testGetWeeklyReport_WithSnapshotExercises_TwoWorkoutDaysSameMuscleGroupDifferentExercises() throws Exception {
        LocalDate weekStart = DbTestFixture.getCurrentWeekStart();
        Long routineId = DbTestFixture.createRoutine(dbConnection, "Two Days Chest Routine", null, true);
        registerRoutineForCleanup(routineId);
        Long day1Id = DbTestFixture.createWorkoutDay(dbConnection, routineId, 1, "Push Day 1", null);
        Long day2Id = DbTestFixture.createWorkoutDay(dbConnection, routineId, 2, "Push Day 2", null);
        Long snapshotId = DbTestFixture.createSnapshot(dbConnection, routineId, weekStart);
        Long snapshotDay1Id = DbTestFixture.createSnapshotWorkoutDay(dbConnection, snapshotId, day1Id, 1, "Push Day 1", null);
        Long snapshotDay2Id = DbTestFixture.createSnapshotWorkoutDay(dbConnection, snapshotId, day2Id, 2, "Push Day 2", null);
        // Day 1: Chest - Bench Press 3 sets
        DbTestFixture.createSnapshotExercise(dbConnection, snapshotDay1Id, 1, "Bench Press", 1, 30, 135.0, 3, null);
        // Day 2: Chest (same muscle group) - Incline Press 2 sets
        DbTestFixture.createSnapshotExercise(dbConnection, snapshotDay2Id, 2, "Incline Press", 1, 24, 95.0, 2, null);

        Response response = apiClient.reports().getWeeklyReport().routineIdPath(routineId).execute(r -> r);
        assertEquals(response.getStatusCode(), 200, "GET /routines/{id}/weekly-report (two days same muscle) - status: " + response.getStatusCode() + ", body: " + response.getBody().asString());
        WeeklyReportResponse report = response.as(WeeklyReportResponse.class);
        assertNotNull(report);
        assertTrue(report.getHasSnapshot());
        assertNotNull(report.getExerciseTotals());
        assertEquals(report.getExerciseTotals().size(), 2, "Should have two exercise totals (Bench Press from day 1, Incline Press from day 2)");

        ExerciseTotal benchPress = report.getExerciseTotals().stream().filter(e -> "Bench Press".equals(e.getExerciseName())).findFirst().orElse(null);
        ExerciseTotal inclinePress = report.getExerciseTotals().stream().filter(e -> "Incline Press".equals(e.getExerciseName())).findFirst().orElse(null);
        assertNotNull(benchPress, "Bench Press (day 1) should be in exerciseTotals");
        assertNotNull(inclinePress, "Incline Press (day 2) should be in exerciseTotals");
        assertEquals(benchPress.getTotalReps().intValue(), 30);
        assertEquals(benchPress.getTotalWeight().doubleValue(), 135.0, 0.01);
        assertEquals(benchPress.getMuscleGroup().getId(), Long.valueOf(1L));
        assertEquals(inclinePress.getTotalReps().intValue(), 24);
        assertEquals(inclinePress.getTotalWeight().doubleValue(), 95.0, 0.01);
        assertEquals(inclinePress.getMuscleGroup().getId(), Long.valueOf(1L));

        // Chest totalSets = 3 (day 1) + 2 (day 2) = 5 across both workout days
        MuscleGroupTotal chestTotal = report.getMuscleGroupTotals().stream()
                .filter(t -> t.getMuscleGroup() != null && t.getMuscleGroup().getId() == 1L)
                .findFirst().orElse(null);
        assertNotNull(chestTotal);
        assertEquals(chestTotal.getTotalSets().intValue(), 5, "Chest totalSets should be 5 (3 from day 1 + 2 from day 2)");
    }

    @Test(description = "GET /routines/{id}/weekly-report - Two workout days, same muscle group, same exercise name: aggregated to single record")
    public void testGetWeeklyReport_WithSnapshotExercises_TwoWorkoutDaysSameMuscleGroupSameExercise() throws Exception {
        LocalDate weekStart = DbTestFixture.getCurrentWeekStart();
        Long routineId = DbTestFixture.createRoutine(dbConnection, "Two Days Same Exercise Routine", null, true);
        registerRoutineForCleanup(routineId);
        Long day1Id = DbTestFixture.createWorkoutDay(dbConnection, routineId, 1, "Push Day 1", null);
        Long day2Id = DbTestFixture.createWorkoutDay(dbConnection, routineId, 2, "Push Day 2", null);
        Long snapshotId = DbTestFixture.createSnapshot(dbConnection, routineId, weekStart);
        Long snapshotDay1Id = DbTestFixture.createSnapshotWorkoutDay(dbConnection, snapshotId, day1Id, 1, "Push Day 1", null);
        Long snapshotDay2Id = DbTestFixture.createSnapshotWorkoutDay(dbConnection, snapshotId, day2Id, 2, "Push Day 2", null);
        // Day 1: Chest - Bench Press 30 reps, 135 weight, 3 sets
        DbTestFixture.createSnapshotExercise(dbConnection, snapshotDay1Id, 1, "Bench Press", 1, 30, 135.0, 3, null);
        // Day 2: Chest - same exercise name "Bench Press", different volume: 24 reps, 95 weight, 2 sets
        DbTestFixture.createSnapshotExercise(dbConnection, snapshotDay2Id, 2, "Bench Press", 1, 24, 95.0, 2, null);

        Response response = apiClient.reports().getWeeklyReport().routineIdPath(routineId).execute(r -> r);
        assertEquals(response.getStatusCode(), 200, "GET /routines/{id}/weekly-report (two days same exercise) - status: " + response.getStatusCode() + ", body: " + response.getBody().asString());
        WeeklyReportResponse report = response.as(WeeklyReportResponse.class);
        assertNotNull(report);
        assertTrue(report.getHasSnapshot());
        assertNotNull(report.getExerciseTotals());
        // Same exercise name on both days -> aggregated into one record (totalReps and totalWeight summed)
        assertEquals(report.getExerciseTotals().size(), 1, "Same exercise name should be aggregated to single record");

        List<ExerciseTotal> benchPressEntries = report.getExerciseTotals().stream()
                .filter(e -> "Bench Press".equals(e.getExerciseName()))
                .toList();
        assertEquals(benchPressEntries.size(), 1, "Bench Press should appear once (aggregated across days)");
        ExerciseTotal benchPress = benchPressEntries.get(0);
        assertEquals(benchPress.getTotalReps().intValue(), 54, "totalReps should be 30 + 24 (day 1 + day 2)");
        assertEquals(benchPress.getTotalWeight().doubleValue(), 230.0, 0.01, "totalWeight should be 135 + 95 (day 1 + day 2)");
        assertNotNull(benchPress.getMuscleGroup());
        assertEquals(benchPress.getMuscleGroup().getId(), Long.valueOf(1L), "Chest");

        // Chest totalSets = 3 (day 1) + 2 (day 2) = 5
        MuscleGroupTotal chestTotal = report.getMuscleGroupTotals().stream()
                .filter(t -> t.getMuscleGroup() != null && t.getMuscleGroup().getId() == 1L)
                .findFirst().orElse(null);
        assertNotNull(chestTotal);
        assertEquals(chestTotal.getTotalSets().intValue(), 5, "Chest totalSets should be 5 (3 from day 1 + 2 from day 2)");
    }

    @Test(description = "GET /routines/{id}/weekly-report - Should isolate sets between routines")
    public void testGetWeeklyReport_RoutineIsolation() throws Exception {
        LocalDate weekStart = DbTestFixture.getCurrentWeekStart();
        Long routine1Id = DbTestFixture.createRoutine(dbConnection, "Isolation Routine 1", null, true);
        Long routine2Id = DbTestFixture.createRoutine(dbConnection, "Isolation Routine 2", null, true);
        registerRoutineForCleanup(routine1Id);
        registerRoutineForCleanup(routine2Id);
        Long day1Id = DbTestFixture.createWorkoutDay(dbConnection, routine1Id, 1, "Push", null);
        Long set1Id = DbTestFixture.createWorkoutDaySet(dbConnection, day1Id, 1, 5, null);
        Long snap1Id = DbTestFixture.createSnapshot(dbConnection, routine1Id, weekStart);
        Long snapDay1Id = DbTestFixture.createSnapshotWorkoutDay(dbConnection, snap1Id, day1Id, 1, "Push", null);
        DbTestFixture.createSnapshotWorkoutDaySet(dbConnection, snapDay1Id, set1Id, 1, 5, null);

        Long day2Id = DbTestFixture.createWorkoutDay(dbConnection, routine2Id, 1, "Push", null);
        Long set2Id = DbTestFixture.createWorkoutDaySet(dbConnection, day2Id, 1, 3, null);
        Long snap2Id = DbTestFixture.createSnapshot(dbConnection, routine2Id, weekStart);
        Long snapDay2Id = DbTestFixture.createSnapshotWorkoutDay(dbConnection, snap2Id, day2Id, 1, "Push", null);
        DbTestFixture.createSnapshotWorkoutDaySet(dbConnection, snapDay2Id, set2Id, 1, 3, null);

        Response response1 = apiClient.reports().getWeeklyReport().routineIdPath(routine1Id).execute(r -> r);
        Response response2 = apiClient.reports().getWeeklyReport().routineIdPath(routine2Id).execute(r -> r);
        assertEquals(response1.getStatusCode(), 200, "GET /routines/{id}/weekly-report (routine1) - status: " + response1.getStatusCode() + ", body: " + response1.getBody().asString());
        assertEquals(response2.getStatusCode(), 200, "GET /routines/{id}/weekly-report (routine2) - status: " + response2.getStatusCode() + ", body: " + response2.getBody().asString());
        WeeklyReportResponse report1 = response1.as(WeeklyReportResponse.class);
        WeeklyReportResponse report2 = response2.as(WeeklyReportResponse.class);

        assertNotNull(report1);
        assertNotNull(report2);
        assertEquals(report1.getRoutineId(), routine1Id);
        assertEquals(report2.getRoutineId(), routine2Id);

        MuscleGroupTotal chest1 = report1.getMuscleGroupTotals().stream()
                .filter(t -> t.getMuscleGroup() != null && t.getMuscleGroup().getId() == 1L).findFirst().orElse(null);
        MuscleGroupTotal chest2 = report2.getMuscleGroupTotals().stream()
                .filter(t -> t.getMuscleGroup() != null && t.getMuscleGroup().getId() == 1L).findFirst().orElse(null);
        assertNotNull(chest1);
        assertNotNull(chest2);
        assertEquals(chest1.getTotalSets().intValue(), 5, "Routine 1 should have 5 sets only");
        assertEquals(chest2.getTotalSets().intValue(), 3, "Routine 2 should have 3 sets only");
    }

    @Test(description = "GET /routines/{id}/weekly-report - Should return 404 for non-existent routine")
    public void testGetWeeklyReport_NotFound() {
        Response response = apiClient.reports().getWeeklyReport().routineIdPath(999999L).execute(r -> r);
        assertEquals(response.getStatusCode(), 404, "Should return 404 for non-existent routine");
    }

    @Test(description = "GET /exercises?workoutDayId= - Should return 200 with workout day from DB")
    public void testGetExercises_ByWorkoutDayId() throws Exception {
        Long routineId = DbTestFixture.createRoutine(dbConnection, "Exercises Routine", null, true);
        registerRoutineForCleanup(routineId);
        Long dayId = DbTestFixture.createWorkoutDay(dbConnection, routineId, 1, "Push Day", null);

        List<Exercise> exercises = apiClient.exercises().getExercises().workoutDayIdQuery(dayId).executeAs(r -> r);
        assertNotNull(exercises, "Exercises list should not be null");
        assertTrue(exercises.size() >= 0, "Should return valid list");
    }

    @Test(description = "GET /exercises without workoutDayId - Should return 400")
    public void testGetExercises_MissingWorkoutDayId_Returns400() {
        // Call without workoutDayIdQuery() so request has no workoutDayId param; API contract requires it -> 400
        Response response = apiClient.exercises().getExercises().execute(r -> r);
        assertEquals(response.getStatusCode(), 400, "Should return 400 when workoutDayId missing");
    }

    /**
     * DEBUG: Verifies fixture and service see the same DB.
     * Fixture inserts a row; if the API returns 404, the service is reading from a different DB.
     * To verify manually: DB_CONTAINER=read-service-xq-fitness-db-1 ./scripts/query-db.sh "SELECT id, name FROM workout_routines;"
     */
    @Test(description = "DEBUG: Fixture and service must see same DB", enabled = true)
    public void testDebug_DbConnection_VerifyFixtureAndServiceSeeSameDb() throws Exception {
        String connectionUrl = DbTestFixture.getConnectionUrl();
        System.err.println("[DEBUG] DbTestFixture connection: " + connectionUrl);
        System.err.println("[DEBUG] API_BASE_URL: " + BASE_URL);

        Long routineId = DbTestFixture.createRoutine(dbConnection, "Debug Same-DB Check", null, true);
        assertNotNull(routineId, "Fixture must be able to insert");
        registerRoutineForCleanup(routineId);

        boolean fixtureSeesRow = DbTestFixture.routineExists(dbConnection, routineId);
        assertTrue(fixtureSeesRow, "Fixture must see its own insert (routineId=" + routineId + ")");

        Response response = apiClient.routines().getRoutineById().routineIdPath(routineId).execute(r -> r);
        assertEquals(
                response.getStatusCode(),
                200,
                "Service returned " + response.getStatusCode() + " (expected 200). Fixture and service may be using different DBs. "
                        + "Fixture URL: " + connectionUrl + ". "
                        + "Verify: DB_CONTAINER=read-service-xq-fitness-db-1 ./scripts/query-db.sh \"SELECT id, name FROM workout_routines;\"");
    }
}
