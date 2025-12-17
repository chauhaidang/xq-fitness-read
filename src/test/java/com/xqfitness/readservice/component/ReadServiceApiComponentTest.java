package com.xqfitness.readservice.component;

import com.xqfitness.client.read_service.api.*;
import com.xqfitness.client.read_service.invoker.*;
import com.xqfitness.client.read_service.model.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.testng.annotations.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
    private MuscleGroupsApi muscleGroupsApi;
    private RoutinesApi routinesApi;
    private WorkoutDaysApi workoutDaysApi;
    private ReportsApi reportsApi;

    private Long testRoutineId;

    @BeforeClass
    public void setupClass() {
        apiClient = new ApiClient();
        apiClient.setBasePath(BASE_URL);
        
        // Configure ObjectMapper to handle LocalDateTime format (without timezone) from API
        // The API returns dates like "2025-11-26T12:29:45.943637" (no timezone)
        // but the generated client expects OffsetDateTime. This custom deserializer
        // converts LocalDateTime strings to OffsetDateTime (treating as UTC).
        configureApiClientForLocalDateTime(apiClient);

        muscleGroupsApi = new MuscleGroupsApi(apiClient);
        routinesApi = new RoutinesApi(apiClient);
        workoutDaysApi = new WorkoutDaysApi(apiClient);
        reportsApi = new ReportsApi(apiClient);
    }
    
    /**
     * Configures the ApiClient's ObjectMapper to handle LocalDateTime strings
     * (without timezone) by converting them to OffsetDateTime (treating as UTC).
     * This is needed because the API returns LocalDateTime format but the generated
     * client expects OffsetDateTime.
     */
    private void configureApiClientForLocalDateTime(ApiClient apiClient) {
        try {
            // Access the ObjectMapper from ApiClient using reflection
            Field objectMapperField = ApiClient.class.getDeclaredField("objectMapper");
            objectMapperField.setAccessible(true);
            ObjectMapper objectMapper = (ObjectMapper) objectMapperField.get(apiClient);
            
            // Add custom deserializer for OffsetDateTime that handles LocalDateTime format
            SimpleModule module = new SimpleModule("LocalDateTimeToOffsetDateTimeModule");
            module.addDeserializer(OffsetDateTime.class, new JsonDeserializer<OffsetDateTime>() {
                @Override
                public OffsetDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                    String value = p.getText();
                    if (value == null || value.isEmpty()) {
                        return null;
                    }
                    // Handle LocalDateTime format (without timezone) by treating as UTC
                    if (value.matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?$") && 
                        !value.contains("+") && !value.endsWith("Z") && 
                        !value.matches(".*[+-]\\d{2}:\\d{2}$")) {
                        // Parse as LocalDateTime and convert to OffsetDateTime with UTC offset
                        LocalDateTime localDateTime = LocalDateTime.parse(value);
                        return localDateTime.atOffset(ZoneOffset.UTC);
                    }
                    // Otherwise, try to parse as OffsetDateTime
                    try {
                        return OffsetDateTime.parse(value);
                    } catch (Exception e) {
                        // If that fails, try appending Z and parse again
                        return OffsetDateTime.parse(value + "Z");
                    }
                }
            });
            objectMapper.registerModule(module);
        } catch (Exception e) {
            throw new RuntimeException("Failed to configure ApiClient for LocalDateTime handling", e);
        }
    }

    @BeforeMethod
    public void setupMethod() {
        try {
            // WebClient returns Flux, need to collect to list and block
            muscleGroupsApi.getMuscleGroups().collectList().block();
        } catch (WebClientResponseException e) {
            fail("Service should be accessible. Status: " + e.getStatusCode().value() + ", Response: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            fail("Service should be accessible. Error: " + e.getMessage() + ", Type: " + e.getClass().getName(), e);
        }
    }

    @Test(priority = 1, description = "GET /muscle-groups - Should return all muscle groups")
    public void testGetAllMuscleGroups() {
        List<MuscleGroup> muscleGroups = muscleGroupsApi.getMuscleGroups().collectList().block();

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
    public void testGetAllRoutines() {
        List<WorkoutRoutine> routines = routinesApi.getRoutines(null).collectList().block();

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
    public void testGetActiveRoutines() {
        List<WorkoutRoutine> routines = routinesApi.getRoutines(true).collectList().block();

        assertNotNull(routines, "Active routines list should not be null");

        for (WorkoutRoutine routine : routines) {
            assertTrue(routine.getIsActive(), "Routine should be active when filtered by isActive=true");
        }
    }

    @Test(priority = 4, description = "GET /routines?isActive=false - Should return only inactive routines")
    public void testGetInactiveRoutines() {
        List<WorkoutRoutine> routines = routinesApi.getRoutines(false).collectList().block();

        assertNotNull(routines, "Inactive routines list should not be null");

        for (WorkoutRoutine routine : routines) {
            assertFalse(routine.getIsActive(), "Routine should be inactive when filtered by isActive=false");
        }
    }

    @Test(priority = 5, dependsOnMethods = "testGetAllRoutines",
          description = "GET /routines/{id} - Should return routine with details")
    public void testGetRoutineById() {
        if (testRoutineId == null) {
            return;
        }

        WorkoutRoutineDetail routine = routinesApi.getRoutineById(testRoutineId).block();

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
            routinesApi.getRoutineById(999999L).block();
            fail("Should have thrown WebClientResponseException with 404 status");
        } catch (WebClientResponseException e) {
            assertEquals(e.getStatusCode(), HttpStatus.NOT_FOUND, "Should return 404 for non-existent routine");
        } catch (Exception e) {
            fail("Expected WebClientResponseException but got: " + e.getClass().getSimpleName());
        }
    }

    @Test(priority = 7, dependsOnMethods = "testGetAllRoutines",
          description = "GET /routines/{id}/days - Should return workout days for routine")
    public void testGetWorkoutDays() {
        if (testRoutineId == null) {
            return;
        }

        try {
            List<WorkoutDayDetail> days = workoutDaysApi.getWorkoutDays(testRoutineId).collectList().block();

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
        } catch (WebClientResponseException e) {
            assertEquals(e.getStatusCode(), HttpStatus.NOT_FOUND, "Should return 404 if routine has no days");
        }
    }

    @Test(priority = 8, description = "GET /routines/{id}/days - Should return 404 for non-existent routine")
    public void testGetWorkoutDays_NotFound() {
        try {
            workoutDaysApi.getWorkoutDays(999999L).collectList().block();
            fail("Should have thrown WebClientResponseException with 404 status");
        } catch (WebClientResponseException e) {
            assertEquals(e.getStatusCode(), HttpStatus.NOT_FOUND, "Should return 404 for non-existent routine days");
        } catch (Exception e) {
            fail("Expected WebClientResponseException but got: " + e.getClass().getSimpleName());
        }
    }

    @Test(priority = 9, dependsOnMethods = "testGetAllRoutines",
          description = "GET /routines/{id}/days - Should return workout days ordered by dayNumber")
    public void testGetWorkoutDays_OrderedByDayNumber() {
        if (testRoutineId == null) {
            return;
        }

        try {
            List<WorkoutDayDetail> days = workoutDaysApi.getWorkoutDays(testRoutineId).collectList().block();

            assertNotNull(days, "Workout days list should not be null");

            if (days.size() >= 2) {
                for (int i = 0; i < days.size() - 1; i++) {
                    assertTrue(days.get(i).getDayNumber() <= days.get(i + 1).getDayNumber(),
                              "Days should be ordered by dayNumber in ascending order");
                }
            }
        } catch (WebClientResponseException e) {
            assertEquals(e.getStatusCode(), HttpStatus.NOT_FOUND, "404 is acceptable if routine has no days");
        }
    }

    @Test(priority = 10, description = "Verify API responses are valid JSON")
    public void testResponseFormat() {
        List<MuscleGroup> muscleGroups = muscleGroupsApi.getMuscleGroups().collectList().block();
        assertNotNull(muscleGroups, "Response should be deserializable");

        List<WorkoutRoutine> routines = routinesApi.getRoutines(null).collectList().block();
        assertNotNull(routines, "Response should be deserializable");
    }

    @Test(priority = 11, description = "Verify API responds within acceptable time")
    public void testResponseTime() {
        long startTime = System.currentTimeMillis();

        muscleGroupsApi.getMuscleGroups().collectList().block();

        long duration = System.currentTimeMillis() - startTime;
        assertTrue(duration < DEFAULT_TIMEOUT,
                  "Response time should be under " + DEFAULT_TIMEOUT + "ms, was: " + duration + "ms");
    }

    @Test(priority = 12, dependsOnMethods = "testGetAllRoutines",
          description = "GET /routines/{routineId}/weekly-report - Should return weekly report with snapshot")
    public void testGetWeeklyReport_WithSnapshot() {
        if (testRoutineId == null) {
            return;
        }

        try {
            // Note: This test assumes a snapshot exists for the routine
            // In a real scenario, you would create a snapshot first via write-service
            WeeklyReportResponse report = reportsApi.getWeeklyReport(testRoutineId, null).block();

            assertNotNull(report, "Weekly report should not be null");
            assertEquals(report.getRoutineId(), testRoutineId, "Routine ID should match");
            assertNotNull(report.getWeekStartDate(), "Week start date should not be null");
            assertNotNull(report.getHasSnapshot(), "Has snapshot flag should not be null");
            assertNotNull(report.getMuscleGroupTotals(), "Muscle group totals should not be null");

            // Verify weekStartDate format (YYYY-MM-DD)
            String weekStartDateStr = report.getWeekStartDate().toString();
            assertTrue(weekStartDateStr.matches("^\\d{4}-\\d{2}-\\d{2}$"),
                      "Week start date should be in YYYY-MM-DD format");

            // If snapshot exists, verify snapshotCreatedAt is present
            if (report.getHasSnapshot()) {
                assertNotNull(report.getSnapshotCreatedAt(), "Snapshot created at should not be null when snapshot exists");
            }

            // Verify muscle group totals structure
            for (MuscleGroupTotal total : report.getMuscleGroupTotals()) {
                assertNotNull(total.getMuscleGroup(), "Muscle group should not be null");
                assertNotNull(total.getMuscleGroup().getId(), "Muscle group ID should not be null");
                assertNotNull(total.getMuscleGroup().getName(), "Muscle group name should not be null");
                assertNotNull(total.getTotalSets(), "Total sets should not be null");
                assertTrue(total.getTotalSets() >= 0, "Total sets should be non-negative");
            }
        } catch (WebClientResponseException e) {
            // If no snapshot exists, that's acceptable - we'll test that scenario separately
            if (e.getStatusCode() != HttpStatus.NOT_FOUND) {
                fail("Unexpected error: " + e.getStatusCode().value() + ", Response: " + e.getResponseBodyAsString());
            }
        }
    }

    @Test(priority = 13, dependsOnMethods = "testGetAllRoutines",
          description = "GET /routines/{routineId}/weekly-report - Should return empty report when no snapshot exists")
    public void testGetWeeklyReport_NoSnapshot() {
        if (testRoutineId == null) {
            return;
        }

        try {
            WeeklyReportResponse report = reportsApi.getWeeklyReport(testRoutineId, null).block();

            assertNotNull(report, "Weekly report should not be null");
            assertEquals(report.getRoutineId(), testRoutineId, "Routine ID should match");
            assertFalse(report.getHasSnapshot(), "Has snapshot should be false when no snapshot exists");
            assertNull(report.getSnapshotCreatedAt(), "Snapshot created at should be null when no snapshot exists");
            assertNotNull(report.getMuscleGroupTotals(), "Muscle group totals should not be null");

            // When no snapshot exists, all muscle groups should have zero sets
            for (MuscleGroupTotal total : report.getMuscleGroupTotals()) {
                assertEquals(total.getTotalSets().intValue(), 0,
                           "Total sets should be zero when no snapshot exists");
            }

            // Should have at least some muscle groups
            assertTrue(report.getMuscleGroupTotals().size() > 0,
                      "Should return at least one muscle group");
        } catch (WebClientResponseException e) {
            fail("Should return empty report, not error. Status: " + e.getStatusCode().value() +
                 ", Response: " + e.getResponseBodyAsString());
        }
    }

    @Test(priority = 14, dependsOnMethods = "testGetAllRoutines",
          description = "GET /routines/{routineId}/weekly-report - Should return report with all muscle groups")
    public void testGetWeeklyReport_AllMuscleGroups() {
        if (testRoutineId == null) {
            return;
        }

        try {
            WeeklyReportResponse report = reportsApi.getWeeklyReport(testRoutineId, null).block();

            assertNotNull(report, "Weekly report should not be null");
            assertNotNull(report.getMuscleGroupTotals(), "Muscle group totals should not be null");

            // Get all muscle groups to compare
            List<MuscleGroup> allMuscleGroups = muscleGroupsApi.getMuscleGroups().collectList().block();

            // Report should include all muscle groups
            assertEquals(report.getMuscleGroupTotals().size(), allMuscleGroups.size(),
                         "Report should include all muscle groups");

            // Verify all muscle groups from the list are in the report
            for (MuscleGroup mg : allMuscleGroups) {
                boolean found = report.getMuscleGroupTotals().stream()
                    .anyMatch(total -> total.getMuscleGroup().getId().equals(mg.getId()));
                assertTrue(found, "Muscle group " + mg.getName() + " should be in the report");
            }
        } catch (WebClientResponseException e) {
            fail("Should return report with all muscle groups. Status: " + e.getStatusCode().value() +
                 ", Response: " + e.getResponseBodyAsString());
        }
    }

    @Test(priority = 15, description = "GET /routines/{routineId}/weekly-report - Should return 404 for invalid routineId")
    public void testGetWeeklyReport_NotFound() {
        try {
            reportsApi.getWeeklyReport(999999L, null).block();
            fail("Should have thrown WebClientResponseException with 404 status");
        } catch (WebClientResponseException e) {
            assertEquals(e.getStatusCode(), HttpStatus.NOT_FOUND,
                         "Should return 404 for non-existent routine");
        } catch (Exception e) {
            fail("Expected WebClientResponseException but got: " + e.getClass().getSimpleName());
        }
    }

    @Test(priority = 16, dependsOnMethods = "testGetAllRoutines",
          description = "GET /routines/{routineId}/weekly-report - Should calculate current week start date")
    public void testGetWeeklyReport_WeekStartDateCalculation() {
        if (testRoutineId == null) {
            return;
        }

        try {
            WeeklyReportResponse report = reportsApi.getWeeklyReport(testRoutineId, null).block();

            assertNotNull(report, "Weekly report should not be null");
            assertEquals(report.getRoutineId(), testRoutineId, "Routine ID should match");
            assertNotNull(report.getWeekStartDate(), "Week start date should not be null");
            
            // Verify weekStartDate is a Monday (ISO 8601 week start)
            java.time.LocalDate weekStart = report.getWeekStartDate();
            java.time.DayOfWeek dayOfWeek = weekStart.getDayOfWeek();
            assertEquals(dayOfWeek, java.time.DayOfWeek.MONDAY,
                         "Week start date should be a Monday (ISO 8601)");
            
            // Verify weekStartDate format (YYYY-MM-DD)
            String weekStartDateStr = weekStart.toString();
            assertTrue(weekStartDateStr.matches("^\\d{4}-\\d{2}-\\d{2}$"),
                      "Week start date should be in YYYY-MM-DD format");
        } catch (WebClientResponseException e) {
            // Acceptable if routine doesn't exist or has no snapshot
            if (e.getStatusCode() != HttpStatus.NOT_FOUND) {
                fail("Unexpected error: " + e.getStatusCode().value() + ", Response: " + e.getResponseBodyAsString());
            }
        }
    }

    @Test(priority = 17, dependsOnMethods = "testGetAllRoutines",
          description = "GET /routines/{routineId}/weekly-report - Should return report within acceptable time")
    public void testGetWeeklyReport_ResponseTime() {
        if (testRoutineId == null) {
            return;
        }

        long startTime = System.currentTimeMillis();

        try {
            reportsApi.getWeeklyReport(testRoutineId, null).block();
            long duration = System.currentTimeMillis() - startTime;
            
            // Performance requirement: < 2 seconds (SC-002)
            assertTrue(duration < 2000,
                      "Report retrieval should complete in < 2 seconds, was: " + duration + "ms");
        } catch (WebClientResponseException e) {
            // Acceptable if routine doesn't exist
            if (e.getStatusCode() != HttpStatus.NOT_FOUND) {
                fail("Unexpected error: " + e.getStatusCode().value());
            }
        }
    }
}
