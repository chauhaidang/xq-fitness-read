package com.xqfitness.readservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xqfitness.readservice.dto.MuscleGroupDTO;
import com.xqfitness.readservice.dto.WorkoutDayDetailDTO;
import com.xqfitness.readservice.dto.WorkoutRoutineDTO;
import com.xqfitness.readservice.dto.WorkoutRoutineDetailDTO;
import com.xqfitness.readservice.service.ReadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReadController.class)
@DisplayName("ReadController Unit Tests")
class ReadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReadService readService;

    private MuscleGroupDTO muscleGroupDTO1;
    private MuscleGroupDTO muscleGroupDTO2;
    private WorkoutRoutineDTO activeRoutineDTO;
    private WorkoutRoutineDTO inactiveRoutineDTO;
    private WorkoutRoutineDetailDTO routineDetailDTO;
    private WorkoutDayDetailDTO workoutDayDetailDTO1;
    private WorkoutDayDetailDTO workoutDayDetailDTO2;

    @BeforeEach
    void setUp() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // Setup MuscleGroupDTOs
        muscleGroupDTO1 = new MuscleGroupDTO(
                1,
                "Chest",
                "Chest muscles",
                now
        );

        muscleGroupDTO2 = new MuscleGroupDTO(
                2,
                "Back",
                "Back muscles",
                now
        );

        // Setup WorkoutRoutineDTOs
        activeRoutineDTO = new WorkoutRoutineDTO(
                1,
                "5-Day Split",
                "5 day workout split",
                true,
                now,
                now
        );

        inactiveRoutineDTO = new WorkoutRoutineDTO(
                2,
                "3-Day Split",
                "3 day workout split",
                false,
                now,
                now
        );

        // Setup WorkoutDayDetailDTOs
        workoutDayDetailDTO1 = new WorkoutDayDetailDTO(
                1,
                1,
                1,
                "Chest Day",
                "Focus on compound movements",
                now,
                now,
                new ArrayList<>(),
                new ArrayList<>()
        );

        workoutDayDetailDTO2 = new WorkoutDayDetailDTO(
                2,
                1,
                2,
                "Back Day",
                "Pull exercises",
                now,
                now,
                new ArrayList<>(),
                new ArrayList<>()
        );

        // Setup WorkoutRoutineDetailDTO
        routineDetailDTO = new WorkoutRoutineDetailDTO(
                1,
                "5-Day Split",
                "5 day workout split",
                true,
                now,
                now,
                Arrays.asList(workoutDayDetailDTO1, workoutDayDetailDTO2)
        );
    }

    @Test
    @DisplayName("GET /muscle-groups - should return 200 OK with list of muscle groups")
    void getMuscleGroups_shouldReturn200WithListOfMuscleGroups() throws Exception {
        // Given
        List<MuscleGroupDTO> muscleGroups = Arrays.asList(muscleGroupDTO1, muscleGroupDTO2);
        when(readService.getAllMuscleGroups()).thenReturn(muscleGroups);

        // When & Then
        mockMvc.perform(get("/muscle-groups")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Chest"))
                .andExpect(jsonPath("$[0].description").value("Chest muscles"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].name").value("Back"));

        verify(readService, times(1)).getAllMuscleGroups();
    }

    @Test
    @DisplayName("GET /muscle-groups - should return 200 OK with empty list when no muscle groups exist")
    void getMuscleGroups_shouldReturn200WithEmptyListWhenNoMuscleGroupsExist() throws Exception {
        // Given
        when(readService.getAllMuscleGroups()).thenReturn(new ArrayList<>());

        // When & Then
        mockMvc.perform(get("/muscle-groups")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));

        verify(readService, times(1)).getAllMuscleGroups();
    }

    @Test
    @DisplayName("GET /routines - should return all routines when isActive param is not provided")
    void getRoutines_shouldReturnAllRoutinesWhenIsActiveParamNotProvided() throws Exception {
        // Given
        List<WorkoutRoutineDTO> routines = Arrays.asList(activeRoutineDTO, inactiveRoutineDTO);
        when(readService.getAllRoutines(null)).thenReturn(routines);

        // When & Then
        mockMvc.perform(get("/routines")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("5-Day Split"))
                .andExpect(jsonPath("$[0].isActive").value(true))
                .andExpect(jsonPath("$[1].name").value("3-Day Split"))
                .andExpect(jsonPath("$[1].isActive").value(false));

        verify(readService, times(1)).getAllRoutines(null);
    }

    @Test
    @DisplayName("GET /routines?isActive=true - should return only active routines")
    void getRoutines_shouldReturnOnlyActiveRoutines() throws Exception {
        // Given
        List<WorkoutRoutineDTO> activeRoutines = Arrays.asList(activeRoutineDTO);
        when(readService.getAllRoutines(true)).thenReturn(activeRoutines);

        // When & Then
        mockMvc.perform(get("/routines")
                        .param("isActive", "true")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("5-Day Split"))
                .andExpect(jsonPath("$[0].isActive").value(true));

        verify(readService, times(1)).getAllRoutines(true);
    }

    @Test
    @DisplayName("GET /routines?isActive=false - should return only inactive routines")
    void getRoutines_shouldReturnOnlyInactiveRoutines() throws Exception {
        // Given
        List<WorkoutRoutineDTO> inactiveRoutines = Arrays.asList(inactiveRoutineDTO);
        when(readService.getAllRoutines(false)).thenReturn(inactiveRoutines);

        // When & Then
        mockMvc.perform(get("/routines")
                        .param("isActive", "false")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("3-Day Split"))
                .andExpect(jsonPath("$[0].isActive").value(false));

        verify(readService, times(1)).getAllRoutines(false);
    }

    @Test
    @DisplayName("GET /routines - should return empty list when no routines exist")
    void getRoutines_shouldReturnEmptyListWhenNoRoutinesExist() throws Exception {
        // Given
        when(readService.getAllRoutines(any())).thenReturn(new ArrayList<>());

        // When & Then
        mockMvc.perform(get("/routines")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));

        verify(readService, times(1)).getAllRoutines(null);
    }

    @Test
    @DisplayName("GET /routines/{routineId} - should return 200 OK with routine details for valid ID")
    void getRoutineById_shouldReturn200WithRoutineDetailsForValidId() throws Exception {
        // Given
        when(readService.getRoutineById(1)).thenReturn(Optional.of(routineDetailDTO));

        // When & Then
        mockMvc.perform(get("/routines/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("5-Day Split"))
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.workoutDays", hasSize(2)))
                .andExpect(jsonPath("$.workoutDays[0].dayName").value("Chest Day"))
                .andExpect(jsonPath("$.workoutDays[1].dayName").value("Back Day"));

        verify(readService, times(1)).getRoutineById(1);
    }

    @Test
    @DisplayName("GET /routines/{routineId} - should return 404 NOT FOUND for non-existent routine")
    void getRoutineById_shouldReturn404ForNonExistentRoutine() throws Exception {
        // Given
        when(readService.getRoutineById(999)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/routines/999")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(readService, times(1)).getRoutineById(999);
    }

    @Test
    @DisplayName("GET /routines/{routineId}/days - should return 200 OK with workout days for valid routine")
    void getWorkoutDays_shouldReturn200WithWorkoutDaysForValidRoutine() throws Exception {
        // Given
        List<WorkoutDayDetailDTO> workoutDays = Arrays.asList(workoutDayDetailDTO1, workoutDayDetailDTO2);
        when(readService.getWorkoutDaysByRoutineId(1)).thenReturn(workoutDays);

        // When & Then
        mockMvc.perform(get("/routines/1/days")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].routineId").value(1))
                .andExpect(jsonPath("$[0].dayNumber").value(1))
                .andExpect(jsonPath("$[0].dayName").value("Chest Day"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].dayNumber").value(2))
                .andExpect(jsonPath("$[1].dayName").value("Back Day"));

        verify(readService, times(1)).getWorkoutDaysByRoutineId(1);
    }

    @Test
    @DisplayName("GET /routines/{routineId}/days - should return 404 NOT FOUND when routine has no days")
    void getWorkoutDays_shouldReturn404WhenRoutineHasNoDays() throws Exception {
        // Given
        when(readService.getWorkoutDaysByRoutineId(1)).thenReturn(new ArrayList<>());

        // When & Then
        mockMvc.perform(get("/routines/1/days")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(readService, times(1)).getWorkoutDaysByRoutineId(1);
    }

    @Test
    @DisplayName("GET /routines/{routineId}/days - should return 404 NOT FOUND for non-existent routine")
    void getWorkoutDays_shouldReturn404ForNonExistentRoutine() throws Exception {
        // Given
        when(readService.getWorkoutDaysByRoutineId(999)).thenReturn(new ArrayList<>());

        // When & Then
        mockMvc.perform(get("/routines/999/days")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(readService, times(1)).getWorkoutDaysByRoutineId(999);
    }
}