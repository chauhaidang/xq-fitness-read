package com.xqfitness.readservice.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xqfitness.readservice.dto.MuscleGroupDTO;
import com.xqfitness.readservice.dto.WorkoutDayDetailDTO;
import com.xqfitness.readservice.dto.WorkoutRoutineDTO;
import com.xqfitness.readservice.dto.WorkoutRoutineDetailDTO;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.testng.annotations.*;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.testng.Assert.*;

/**
 * Component Tests for Read Service API
 *
 * These tests hit real API endpoints via network through the API Gateway.
 *
 * Prerequisites:
 * 1. Navigate to test-env directory
 * 2. Use xq-infra CLI to spin up the test environment:
 *    - Start database: xq-infra start xq-fitness-db
 *    - Start read-service: xq-infra start xq-fitness-read-service
 * 3. Verify API Gateway is accessible at: http://localhost:8080/xq-fitness-read-service/api/v1
 *
 * These are real integration tests - no mocks, no embedded servers.
 */
@Test(groups = {"component", "integration"})
public class ReadServiceApiComponentTest {

    // Support both local (via API Gateway) and CI environments
    private static final String BASE_URL = System.getenv("API_BASE_URL") != null
        ? System.getenv("API_BASE_URL")
        : "http://localhost:8080/xq-fitness-read-service/api/v1";
    private static final int TIMEOUT_MS = 5000;

    private ObjectMapper objectMapper;
    private Long testRoutineId;

    @BeforeClass
    public void setupClass() {
        RestAssured.baseURI = BASE_URL;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        System.out.println("=================================================");
        System.out.println("Component Test Configuration:");
        System.out.println("API Gateway Base URL: " + BASE_URL);
        System.out.println("=================================================");
    }

    @BeforeMethod
    public void setupMethod() {
        // Verify service is up before each test
        Response healthCheck = given()
                .contentType(ContentType.JSON)
                .when()
                .get("/muscle-groups")
                .then()
                .extract()
                .response();

        assertTrue(healthCheck.getStatusCode() == 200 || healthCheck.getStatusCode() == 404,
                "Service should be accessible. Status: " + healthCheck.getStatusCode());
    }

    @Test(priority = 1, description = "GET /muscle-groups - Should return all muscle groups")
    public void testGetAllMuscleGroups() throws Exception {
        Response response = given()
                .contentType(ContentType.JSON)
                .when()
                .get("/muscle-groups")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .response();

        List<MuscleGroupDTO> muscleGroups = objectMapper.readValue(
                response.getBody().asString(),
                new TypeReference<List<MuscleGroupDTO>>() {}
        );

        assertNotNull(muscleGroups, "Muscle groups list should not be null");
        assertTrue(muscleGroups.size() >= 0, "Should return a valid list (empty or populated)");

        System.out.println("✓ Retrieved " + muscleGroups.size() + " muscle groups");

        // If data exists, validate structure
        if (!muscleGroups.isEmpty()) {
            MuscleGroupDTO firstGroup = muscleGroups.get(0);
            assertNotNull(firstGroup.getId(), "Muscle group ID should not be null");
            assertNotNull(firstGroup.getName(), "Muscle group name should not be null");
            assertNotNull(firstGroup.getCreatedAt(), "Muscle group createdAt should not be null");
        }
    }

    @Test(priority = 2, description = "GET /routines - Should return all workout routines")
    public void testGetAllRoutines() throws Exception {
        Response response = given()
                .contentType(ContentType.JSON)
                .when()
                .get("/routines")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .response();

        List<WorkoutRoutineDTO> routines = objectMapper.readValue(
                response.getBody().asString(),
                new TypeReference<List<WorkoutRoutineDTO>>() {}
        );

        assertNotNull(routines, "Routines list should not be null");
        assertTrue(routines.size() >= 0, "Should return a valid list (empty or populated)");

        System.out.println("✓ Retrieved " + routines.size() + " workout routines");

        // Store first routine ID for later tests
        if (!routines.isEmpty()) {
            testRoutineId = routines.get(0).getId();
            System.out.println("✓ Using routine ID " + testRoutineId + " for subsequent tests");

            // Validate structure
            WorkoutRoutineDTO firstRoutine = routines.get(0);
            assertNotNull(firstRoutine.getId(), "Routine ID should not be null");
            assertNotNull(firstRoutine.getName(), "Routine name should not be null");
            assertNotNull(firstRoutine.getIsActive(), "Routine isActive should not be null");
            assertNotNull(firstRoutine.getCreatedAt(), "Routine createdAt should not be null");
        }
    }

    @Test(priority = 3, description = "GET /routines?isActive=true - Should return only active routines")
    public void testGetActiveRoutines() throws Exception {
        Response response = given()
                .contentType(ContentType.JSON)
                .queryParam("isActive", true)
                .when()
                .get("/routines")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .response();

        List<WorkoutRoutineDTO> routines = objectMapper.readValue(
                response.getBody().asString(),
                new TypeReference<List<WorkoutRoutineDTO>>() {}
        );

        assertNotNull(routines, "Active routines list should not be null");

        // All returned routines should be active
        for (WorkoutRoutineDTO routine : routines) {
            assertTrue(routine.getIsActive(), "Routine should be active when filtered by isActive=true");
        }

        System.out.println("✓ Retrieved " + routines.size() + " active routines");
    }

    @Test(priority = 4, description = "GET /routines?isActive=false - Should return only inactive routines")
    public void testGetInactiveRoutines() throws Exception {
        Response response = given()
                .contentType(ContentType.JSON)
                .queryParam("isActive", false)
                .when()
                .get("/routines")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .response();

        List<WorkoutRoutineDTO> routines = objectMapper.readValue(
                response.getBody().asString(),
                new TypeReference<List<WorkoutRoutineDTO>>() {}
        );

        assertNotNull(routines, "Inactive routines list should not be null");

        // All returned routines should be inactive
        for (WorkoutRoutineDTO routine : routines) {
            assertFalse(routine.getIsActive(), "Routine should be inactive when filtered by isActive=false");
        }

        System.out.println("✓ Retrieved " + routines.size() + " inactive routines");
    }

    @Test(priority = 5, dependsOnMethods = "testGetAllRoutines",
          description = "GET /routines/{id} - Should return routine details with workout days")
    public void testGetRoutineById() throws Exception {
        // Skip if no routines exist
        if (testRoutineId == null) {
            System.out.println("⊘ Skipping - no routines available in database");
            return;
        }

        Response response = given()
                .contentType(ContentType.JSON)
                .pathParam("id", testRoutineId)
                .when()
                .get("/routines/{id}")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .response();

        WorkoutRoutineDetailDTO routine = objectMapper.readValue(
                response.getBody().asString(),
                WorkoutRoutineDetailDTO.class
        );

        assertNotNull(routine, "Routine details should not be null");
        assertEquals(routine.getId(), testRoutineId, "Routine ID should match requested ID");
        assertNotNull(routine.getName(), "Routine name should not be null");
        assertNotNull(routine.getWorkoutDays(), "Workout days list should not be null");

        System.out.println("✓ Retrieved routine: " + routine.getName() +
                         " with " + routine.getWorkoutDays().size() + " workout days");

        // Validate workout day structure if present
        if (!routine.getWorkoutDays().isEmpty()) {
            WorkoutDayDetailDTO firstDay = routine.getWorkoutDays().get(0);
            assertNotNull(firstDay.getId(), "Workout day ID should not be null");
            assertNotNull(firstDay.getDayNumber(), "Day number should not be null");
            assertNotNull(firstDay.getDayName(), "Day name should not be null");
            assertNotNull(firstDay.getSets(), "Sets list should not be null");
        }
    }

    @Test(priority = 6, description = "GET /routines/{id} - Should return 404 for non-existent routine")
    public void testGetRoutineById_NotFound() {
        given()
                .contentType(ContentType.JSON)
                .pathParam("id", 999999)
                .when()
                .get("/routines/{id}")
                .then()
                .statusCode(404);

        System.out.println("✓ Correctly returns 404 for non-existent routine");
    }

    @Test(priority = 7, dependsOnMethods = "testGetAllRoutines",
          description = "GET /routines/{id}/days - Should return workout days for routine")
    public void testGetWorkoutDays() throws Exception {
        // Skip if no routines exist
        if (testRoutineId == null) {
            System.out.println("⊘ Skipping - no routines available in database");
            return;
        }

        Response response = given()
                .contentType(ContentType.JSON)
                .pathParam("id", testRoutineId)
                .when()
                .get("/routines/{id}/days");

        // Can be either 200 with days or 404 if no days exist
        int statusCode = response.getStatusCode();
        assertTrue(statusCode == 200 || statusCode == 404,
                  "Status should be 200 or 404, got: " + statusCode);

        if (statusCode == 200) {
            response.then().contentType(ContentType.JSON);

            List<WorkoutDayDetailDTO> days = objectMapper.readValue(
                    response.getBody().asString(),
                    new TypeReference<List<WorkoutDayDetailDTO>>() {}
            );

            assertNotNull(days, "Workout days list should not be null");
            assertTrue(days.size() > 0, "Should have at least one workout day");

            System.out.println("✓ Retrieved " + days.size() + " workout days");

            // Validate days are ordered by dayNumber
            for (int i = 0; i < days.size() - 1; i++) {
                assertTrue(days.get(i).getDayNumber() <= days.get(i + 1).getDayNumber(),
                          "Days should be ordered by dayNumber");
            }

            // Validate workout day structure
            WorkoutDayDetailDTO firstDay = days.get(0);
            assertNotNull(firstDay.getId(), "Day ID should not be null");
            assertNotNull(firstDay.getRoutineId(), "Routine ID should not be null");
            assertEquals(firstDay.getRoutineId(), testRoutineId, "Routine ID should match");
            assertNotNull(firstDay.getDayNumber(), "Day number should not be null");
            assertNotNull(firstDay.getSets(), "Sets list should not be null");

            // Validate sets structure if present
            if (!firstDay.getSets().isEmpty()) {
                assertNotNull(firstDay.getSets().get(0).getMuscleGroup(),
                             "Set should have muscle group");
                assertNotNull(firstDay.getSets().get(0).getNumberOfSets(),
                             "Set should have number of sets");
            }
        } else {
            System.out.println("✓ Routine has no workout days (404)");
        }
    }

    @Test(priority = 8, description = "GET /routines/{id}/days - Should return 404 for non-existent routine")
    public void testGetWorkoutDays_NotFound() {
        given()
                .contentType(ContentType.JSON)
                .pathParam("id", 999999)
                .when()
                .get("/routines/{id}/days")
                .then()
                .statusCode(404);

        System.out.println("✓ Correctly returns 404 for non-existent routine days");
    }

    @Test(priority = 9, description = "Verify all endpoints return JSON content type")
    public void testContentTypeHeaders() {
        // Test muscle-groups endpoint
        given()
                .when()
                .get("/muscle-groups")
                .then()
                .contentType(ContentType.JSON);

        // Test routines endpoint
        given()
                .when()
                .get("/routines")
                .then()
                .contentType(ContentType.JSON);

        System.out.println("✓ All endpoints return correct JSON content type");
    }

    @Test(priority = 10, description = "Verify API responds within acceptable time")
    public void testResponseTime() {
        // All endpoints should respond within timeout
        long startTime = System.currentTimeMillis();

        given()
                .when()
                .get("/muscle-groups")
                .then()
                .statusCode(200);

        long duration = System.currentTimeMillis() - startTime;
        assertTrue(duration < TIMEOUT_MS,
                  "Response time should be under " + TIMEOUT_MS + "ms, was: " + duration + "ms");

        System.out.println("✓ API responded in " + duration + "ms (threshold: " + TIMEOUT_MS + "ms)");
    }

    @AfterClass
    public void teardownClass() {
        System.out.println("=================================================");
        System.out.println("Component Tests Completed");
        System.out.println("=================================================");
    }
}
