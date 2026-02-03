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
import java.time.LocalDate;
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
        for (int attempt = 1; attempt <= REACHABILITY_RETRIES; attempt++) {
            try {
                List<MuscleGroup> muscleGroups = apiClient.muscleGroups().getMuscleGroups().executeAs(r -> r);
                assertNotNull(muscleGroups, "Service should be accessible");
                return;
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
        fail("Service not reachable after " + REACHABILITY_RETRIES + " attempts: " + (lastException != null ? lastException.getMessage() : "unknown"));
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
        Long routineId = null;
        try (Connection conn = DbTestFixture.getConnection()) {
            routineId = DbTestFixture.createRoutine(conn, "Component Test Routine", "Description", true);
            assertNotNull(routineId, "Routine should be created");

            List<WorkoutRoutine> routines = apiClient.routines().getRoutines().executeAs(r -> r);
            assertNotNull(routines, "Routines list should not be null");
            final Long id = routineId;
            WorkoutRoutine found = routines.stream().filter(r -> r.getId().equals(id)).findFirst().orElse(null);
            assertNotNull(found, "Created routine " + id + " should appear in list. Returned IDs: " + routines.stream().map(WorkoutRoutine::getId).toList() + ". Ensure service uses same DB as test (DB_HOST/DB_PORT).");
            assertEquals(found.getName(), "Component Test Routine", "Routine name should match");
            assertTrue(found.getIsActive(), "Routine should be active");
        } finally {
            if (routineId != null) {
                try (Connection conn = DbTestFixture.getConnection()) {
                    DbTestFixture.deleteRoutine(conn, routineId);
                }
            }
        }
    }

    @Test(description = "GET /routines?isActive=true - Should return only active routines")
    public void testGetRoutines_ActiveFilter() throws Exception {
        Long activeId = null;
        Long inactiveId = null;
        try (Connection conn = DbTestFixture.getConnection()) {
            activeId = DbTestFixture.createRoutine(conn, "Active Routine", null, true);
            inactiveId = DbTestFixture.createRoutine(conn, "Inactive Routine", null, false);
            assertNotNull(activeId);
            assertNotNull(inactiveId);

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
        } finally {
            try (Connection conn = DbTestFixture.getConnection()) {
                if (activeId != null) DbTestFixture.deleteRoutine(conn, activeId);
                if (inactiveId != null) DbTestFixture.deleteRoutine(conn, inactiveId);
            }
        }
    }

    @Test(description = "GET /routines/{id} - Should return routine with days and sets from DB")
    public void testGetRoutineById_ReturnsDetail() throws Exception {
        Long routineId = null;
        try (Connection conn = DbTestFixture.getConnection()) {
            routineId = DbTestFixture.createRoutine(conn, "Detail Test Routine", null, true);
            Long dayId = DbTestFixture.createWorkoutDay(conn, routineId, 1, "Push Day", null);
            assertNotNull(dayId);
            Long setId = DbTestFixture.createWorkoutDaySet(conn, dayId, 1, 3, null);
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
        } finally {
            if (routineId != null) {
                try (Connection conn = DbTestFixture.getConnection()) {
                    DbTestFixture.deleteRoutine(conn, routineId);
                }
            }
        }
    }

    @Test(description = "GET /routines/{id} - Should return 404 for non-existent routine")
    public void testGetRoutineById_NotFound() {
        Response response = apiClient.routines().getRoutineById().routineIdPath(999999L).execute(r -> r);
        assertEquals(response.getStatusCode(), 404, "Should return 404 for non-existent routine");
    }

    @Test(description = "GET /routines/{id}/days - Should return days ordered by dayNumber")
    public void testGetWorkoutDays_OrderedByDayNumber() throws Exception {
        Long routineId = null;
        try (Connection conn = DbTestFixture.getConnection()) {
            routineId = DbTestFixture.createRoutine(conn, "Days Test Routine", null, true);
            DbTestFixture.createWorkoutDay(conn, routineId, 2, "Day Two", null);
            DbTestFixture.createWorkoutDay(conn, routineId, 1, "Day One", null);

            Response response = apiClient.workoutDays().getWorkoutDays().routineIdPath(routineId).execute(r -> r);
            assertEquals(response.getStatusCode(), 200, "GET /routines/{id}/days - status: " + response.getStatusCode() + ", body: " + response.getBody().asString());
            Type listType = new TypeToken<List<WorkoutDayDetail>>(){}.getType();
            List<WorkoutDayDetail> days = response.as(listType);
            assertNotNull(days, "Days list should not be null");
            assertEquals(days.size(), 2, "Should have two days");
            assertTrue(days.get(0).getDayNumber() <= days.get(1).getDayNumber(), "Days should be ordered by dayNumber");
            assertEquals(days.get(0).getDayName(), "Day One", "First day should be Day One");
            assertEquals(days.get(1).getDayName(), "Day Two", "Second day should be Day Two");
        } finally {
            if (routineId != null) {
                try (Connection conn = DbTestFixture.getConnection()) {
                    DbTestFixture.deleteRoutine(conn, routineId);
                }
            }
        }
    }

    @Test(description = "GET /routines/{id}/days - Should return 404 for non-existent routine")
    public void testGetWorkoutDays_NotFound() {
        Response response = apiClient.workoutDays().getWorkoutDays().routineIdPath(999999L).execute(r -> r);
        assertEquals(response.getStatusCode(), 404, "Should return 404 for non-existent routine");
    }

    @Test(description = "GET /routines/{id}/weekly-report - Should return empty report when no snapshot")
    public void testGetWeeklyReport_NoSnapshot() throws Exception {
        Long routineId = null;
        try (Connection conn = DbTestFixture.getConnection()) {
            routineId = DbTestFixture.createRoutine(conn, "Report No Snapshot Routine", null, true);

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
        } finally {
            if (routineId != null) {
                try (Connection conn = DbTestFixture.getConnection()) {
                    DbTestFixture.deleteRoutine(conn, routineId);
                }
            }
        }
    }

    @Test(description = "GET /routines/{id}/weekly-report - Should return report with snapshot totals")
    public void testGetWeeklyReport_WithSnapshot() throws Exception {
        Long routineId = null;
        LocalDate weekStart = DbTestFixture.getCurrentWeekStart();
        try (Connection conn = DbTestFixture.getConnection()) {
            routineId = DbTestFixture.createRoutine(conn, "Report Snapshot Routine", null, true);
            Long dayId = DbTestFixture.createWorkoutDay(conn, routineId, 1, "Push Day", null);
            Long setId = DbTestFixture.createWorkoutDaySet(conn, dayId, 1, 5, null); // Chest = 1, 5 sets
            Long snapshotId = DbTestFixture.createSnapshot(conn, routineId, weekStart);
            Long snapshotDayId = DbTestFixture.createSnapshotWorkoutDay(conn, snapshotId, dayId, 1, "Push Day", null);
            DbTestFixture.createSnapshotWorkoutDaySet(conn, snapshotDayId, setId, 1, 5, null);

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
        } finally {
            if (routineId != null) {
                try (Connection conn = DbTestFixture.getConnection()) {
                    DbTestFixture.deleteRoutine(conn, routineId);
                }
            }
        }
    }

    @Test(description = "GET /routines/{id}/weekly-report - Should isolate sets between routines")
    public void testGetWeeklyReport_RoutineIsolation() throws Exception {
        Long routine1Id = null;
        Long routine2Id = null;
        LocalDate weekStart = DbTestFixture.getCurrentWeekStart();
        try (Connection conn = DbTestFixture.getConnection()) {
            routine1Id = DbTestFixture.createRoutine(conn, "Isolation Routine 1", null, true);
            Long day1Id = DbTestFixture.createWorkoutDay(conn, routine1Id, 1, "Push", null);
            Long set1Id = DbTestFixture.createWorkoutDaySet(conn, day1Id, 1, 5, null);
            Long snap1Id = DbTestFixture.createSnapshot(conn, routine1Id, weekStart);
            Long snapDay1Id = DbTestFixture.createSnapshotWorkoutDay(conn, snap1Id, day1Id, 1, "Push", null);
            DbTestFixture.createSnapshotWorkoutDaySet(conn, snapDay1Id, set1Id, 1, 5, null);

            routine2Id = DbTestFixture.createRoutine(conn, "Isolation Routine 2", null, true);
            Long day2Id = DbTestFixture.createWorkoutDay(conn, routine2Id, 1, "Push", null);
            Long set2Id = DbTestFixture.createWorkoutDaySet(conn, day2Id, 1, 3, null);
            Long snap2Id = DbTestFixture.createSnapshot(conn, routine2Id, weekStart);
            Long snapDay2Id = DbTestFixture.createSnapshotWorkoutDay(conn, snap2Id, day2Id, 1, "Push", null);
            DbTestFixture.createSnapshotWorkoutDaySet(conn, snapDay2Id, set2Id, 1, 3, null);

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
        } finally {
            try (Connection conn = DbTestFixture.getConnection()) {
                if (routine1Id != null) DbTestFixture.deleteRoutine(conn, routine1Id);
                if (routine2Id != null) DbTestFixture.deleteRoutine(conn, routine2Id);
            }
        }
    }

    @Test(description = "GET /routines/{id}/weekly-report - Should return 404 for non-existent routine")
    public void testGetWeeklyReport_NotFound() {
        Response response = apiClient.reports().getWeeklyReport().routineIdPath(999999L).execute(r -> r);
        assertEquals(response.getStatusCode(), 404, "Should return 404 for non-existent routine");
    }

    @Test(description = "GET /exercises?workoutDayId= - Should return 200 with workout day from DB")
    public void testGetExercises_ByWorkoutDayId() throws Exception {
        Long routineId = null;
        try (Connection conn = DbTestFixture.getConnection()) {
            routineId = DbTestFixture.createRoutine(conn, "Exercises Routine", null, true);
            Long dayId = DbTestFixture.createWorkoutDay(conn, routineId, 1, "Push Day", null);

            List<Exercise> exercises = apiClient.exercises().getExercises().workoutDayIdQuery(dayId).executeAs(r -> r);
            assertNotNull(exercises, "Exercises list should not be null");
            assertTrue(exercises.size() >= 0, "Should return valid list");
        } finally {
            if (routineId != null) {
                try (Connection conn = DbTestFixture.getConnection()) {
                    DbTestFixture.deleteRoutine(conn, routineId);
                }
            }
        }
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

        Long routineId = null;
        try (Connection conn = DbTestFixture.getConnection()) {
            routineId = DbTestFixture.createRoutine(conn, "Debug Same-DB Check", null, true);
            assertNotNull(routineId, "Fixture must be able to insert");

            boolean fixtureSeesRow = DbTestFixture.routineExists(conn, routineId);
            assertTrue(fixtureSeesRow, "Fixture must see its own insert (routineId=" + routineId + ")");

            Response response = apiClient.routines().getRoutineById().routineIdPath(routineId).execute(r -> r);
            assertEquals(
                    response.getStatusCode(),
                    200,
                    "Service returned " + response.getStatusCode() + " (expected 200). Fixture and service may be using different DBs. "
                            + "Fixture URL: " + connectionUrl + ". "
                            + "Verify: DB_CONTAINER=read-service-xq-fitness-db-1 ./scripts/query-db.sh \"SELECT id, name FROM workout_routines;\"");
        } finally {
            if (routineId != null) {
                try (Connection conn = DbTestFixture.getConnection()) {
                    DbTestFixture.deleteRoutine(conn, routineId);
                }
            }
        }
    }
}
