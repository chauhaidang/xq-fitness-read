package com.xqfitness.readservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xqfitness.readservice.dto.MuscleGroupDTO;
import com.xqfitness.readservice.dto.MuscleGroupTotalDTO;
import com.xqfitness.readservice.dto.WeeklyReportDTO;
import com.xqfitness.readservice.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReportController.class)
@DisplayName("ReportController Unit Tests")
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReportService reportService;

    private WeeklyReportDTO reportWithSnapshot;
    private WeeklyReportDTO reportWithoutSnapshot;
    private MuscleGroupDTO chestMuscleGroup;
    private MuscleGroupDTO backMuscleGroup;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate weekStart = LocalDate.of(2024, 12, 2); // Monday

        chestMuscleGroup = new MuscleGroupDTO(
                1,
                "Chest",
                "Chest muscles",
                now
        );

        backMuscleGroup = new MuscleGroupDTO(
                2,
                "Back",
                "Back muscles",
                now
        );

        // Report with snapshot
        List<MuscleGroupTotalDTO> totalsWithSnapshot = Arrays.asList(
                new MuscleGroupTotalDTO(chestMuscleGroup, 12),
                new MuscleGroupTotalDTO(backMuscleGroup, 8)
        );

        reportWithSnapshot = new WeeklyReportDTO(
                1,
                weekStart,
                true,
                now,
                totalsWithSnapshot
        );

        // Report without snapshot (all zeros)
        List<MuscleGroupTotalDTO> totalsWithoutSnapshot = Arrays.asList(
                new MuscleGroupTotalDTO(chestMuscleGroup, 0),
                new MuscleGroupTotalDTO(backMuscleGroup, 0)
        );

        reportWithoutSnapshot = new WeeklyReportDTO(
                1,
                weekStart,
                false,
                null,
                totalsWithoutSnapshot
        );
    }

    @Test
    @DisplayName("GET /routines/{routineId}/weekly-report - should return 200 OK with report when snapshot exists")
    void getWeeklyReport_shouldReturn200WithReportWhenSnapshotExists() throws Exception {
        // Given
        Integer routineId = 1;
        when(reportService.getWeeklyReport(eq(routineId), any())).thenReturn(reportWithSnapshot);

        // When & Then
        mockMvc.perform(get("/routines/{routineId}/weekly-report", routineId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.routineId").value(1))
                .andExpect(jsonPath("$.weekStartDate").value("2024-12-02"))
                .andExpect(jsonPath("$.hasSnapshot").value(true))
                .andExpect(jsonPath("$.snapshotCreatedAt").exists())
                .andExpect(jsonPath("$.muscleGroupTotals", hasSize(2)))
                .andExpect(jsonPath("$.muscleGroupTotals[0].muscleGroup.name").value("Chest"))
                .andExpect(jsonPath("$.muscleGroupTotals[0].totalSets").value(12))
                .andExpect(jsonPath("$.muscleGroupTotals[1].muscleGroup.name").value("Back"))
                .andExpect(jsonPath("$.muscleGroupTotals[1].totalSets").value(8));

        verify(reportService, times(1)).getWeeklyReport(eq(routineId), any());
    }

    @Test
    @DisplayName("GET /routines/{routineId}/weekly-report - should return 200 OK with empty report when no snapshot exists")
    void getWeeklyReport_shouldReturn200WithEmptyReportWhenNoSnapshotExists() throws Exception {
        // Given
        Integer routineId = 1;
        when(reportService.getWeeklyReport(eq(routineId), any())).thenReturn(reportWithoutSnapshot);

        // When & Then
        mockMvc.perform(get("/routines/{routineId}/weekly-report", routineId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.routineId").value(1))
                .andExpect(jsonPath("$.hasSnapshot").value(false))
                .andExpect(jsonPath("$.snapshotCreatedAt").isEmpty())
                .andExpect(jsonPath("$.muscleGroupTotals", hasSize(2)))
                .andExpect(jsonPath("$.muscleGroupTotals[0].totalSets").value(0))
                .andExpect(jsonPath("$.muscleGroupTotals[1].totalSets").value(0));

        verify(reportService, times(1)).getWeeklyReport(eq(routineId), any());
    }

    @Test
    @DisplayName("GET /routines/{routineId}/weekly-report - should return 200 OK when routine has workout days but no sets")
    void getWeeklyReport_shouldReturn200WhenRoutineHasWorkoutDaysButNoSets() throws Exception {
        // Given
        Integer routineId = 1;
        List<MuscleGroupTotalDTO> emptyTotals = Arrays.asList(
                new MuscleGroupTotalDTO(chestMuscleGroup, 0),
                new MuscleGroupTotalDTO(backMuscleGroup, 0)
        );
        WeeklyReportDTO reportWithNoSets = new WeeklyReportDTO(
                1,
                LocalDate.of(2024, 12, 2),
                true,
                LocalDateTime.now(),
                emptyTotals
        );
        when(reportService.getWeeklyReport(eq(routineId), any())).thenReturn(reportWithNoSets);

        // When & Then
        mockMvc.perform(get("/routines/{routineId}/weekly-report", routineId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.hasSnapshot").value(true))
                .andExpect(jsonPath("$.muscleGroupTotals", hasSize(2)))
                .andExpect(jsonPath("$.muscleGroupTotals[0].totalSets").value(0))
                .andExpect(jsonPath("$.muscleGroupTotals[1].totalSets").value(0));

        verify(reportService, times(1)).getWeeklyReport(eq(routineId), any());
    }

    @Test
    @DisplayName("GET /routines/{routineId}/weekly-report - should return 404 NOT FOUND for invalid routineId")
    void getWeeklyReport_shouldReturn404ForInvalidRoutineId() throws Exception {
        // Given
        Integer invalidRoutineId = 999;
        when(reportService.getWeeklyReport(eq(invalidRoutineId), any()))
                .thenThrow(new IllegalArgumentException("Routine not found: " + invalidRoutineId));

        // When & Then
        mockMvc.perform(get("/routines/{routineId}/weekly-report", invalidRoutineId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(reportService, times(1)).getWeeklyReport(eq(invalidRoutineId), any());
    }
}
