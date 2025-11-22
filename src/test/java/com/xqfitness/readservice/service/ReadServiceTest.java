package com.xqfitness.readservice.service;

import com.xqfitness.readservice.dto.MuscleGroupDTO;
import com.xqfitness.readservice.dto.WorkoutDayDetailDTO;
import com.xqfitness.readservice.dto.WorkoutRoutineDTO;
import com.xqfitness.readservice.dto.WorkoutRoutineDetailDTO;
import com.xqfitness.readservice.entity.MuscleGroup;
import com.xqfitness.readservice.entity.WorkoutDay;
import com.xqfitness.readservice.entity.WorkoutRoutine;
import com.xqfitness.readservice.repository.MuscleGroupRepository;
import com.xqfitness.readservice.repository.WorkoutDayRepository;
import com.xqfitness.readservice.repository.WorkoutRoutineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReadService Unit Tests")
class ReadServiceTest {

    @Mock
    private MuscleGroupRepository muscleGroupRepository;

    @Mock
    private WorkoutRoutineRepository workoutRoutineRepository;

    @Mock
    private WorkoutDayRepository workoutDayRepository;

    @InjectMocks
    private ReadService readService;

    private MuscleGroup muscleGroup1;
    private MuscleGroup muscleGroup2;
    private WorkoutRoutine activeRoutine;
    private WorkoutRoutine inactiveRoutine;
    private WorkoutDay workoutDay1;
    private WorkoutDay workoutDay2;

    @BeforeEach
    void setUp() {
        // Setup MuscleGroups
        muscleGroup1 = new MuscleGroup();
        muscleGroup1.setId(1L);
        muscleGroup1.setName("Chest");
        muscleGroup1.setDescription("Chest muscles");
        muscleGroup1.setCreatedAt(LocalDateTime.now());

        muscleGroup2 = new MuscleGroup();
        muscleGroup2.setId(2L);
        muscleGroup2.setName("Back");
        muscleGroup2.setDescription("Back muscles");
        muscleGroup2.setCreatedAt(LocalDateTime.now());

        // Setup WorkoutRoutines
        activeRoutine = new WorkoutRoutine();
        activeRoutine.setId(1L);
        activeRoutine.setName("5-Day Split");
        activeRoutine.setDescription("5 day workout split");
        activeRoutine.setIsActive(true);
        activeRoutine.setCreatedAt(LocalDateTime.now());
        activeRoutine.setUpdatedAt(LocalDateTime.now());
        activeRoutine.setWorkoutDays(new ArrayList<>());

        inactiveRoutine = new WorkoutRoutine();
        inactiveRoutine.setId(2L);
        inactiveRoutine.setName("3-Day Split");
        inactiveRoutine.setDescription("3 day workout split");
        inactiveRoutine.setIsActive(false);
        inactiveRoutine.setCreatedAt(LocalDateTime.now());
        inactiveRoutine.setUpdatedAt(LocalDateTime.now());
        inactiveRoutine.setWorkoutDays(new ArrayList<>());

        // Setup WorkoutDays
        workoutDay1 = new WorkoutDay();
        workoutDay1.setId(1L);
        workoutDay1.setRoutine(activeRoutine);
        workoutDay1.setDayNumber(1);
        workoutDay1.setDayName("Chest Day");
        workoutDay1.setNotes("Focus on compound movements");
        workoutDay1.setCreatedAt(LocalDateTime.now());
        workoutDay1.setUpdatedAt(LocalDateTime.now());
        workoutDay1.setSets(new ArrayList<>());

        workoutDay2 = new WorkoutDay();
        workoutDay2.setId(2L);
        workoutDay2.setRoutine(activeRoutine);
        workoutDay2.setDayNumber(2);
        workoutDay2.setDayName("Back Day");
        workoutDay2.setNotes("Pull exercises");
        workoutDay2.setCreatedAt(LocalDateTime.now());
        workoutDay2.setUpdatedAt(LocalDateTime.now());
        workoutDay2.setSets(new ArrayList<>());
    }

    @Test
    @DisplayName("getAllMuscleGroups - should return all muscle groups as DTOs")
    void getAllMuscleGroups_shouldReturnAllMuscleGroupsAsDTOs() {
        // Given
        List<MuscleGroup> muscleGroups = Arrays.asList(muscleGroup1, muscleGroup2);
        when(muscleGroupRepository.findAll()).thenReturn(muscleGroups);

        // When
        List<MuscleGroupDTO> result = readService.getAllMuscleGroups();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getName()).isEqualTo("Chest");
        assertThat(result.get(0).getDescription()).isEqualTo("Chest muscles");
        assertThat(result.get(1).getId()).isEqualTo(2L);
        assertThat(result.get(1).getName()).isEqualTo("Back");
        verify(muscleGroupRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("getAllMuscleGroups - should return empty list when no muscle groups exist")
    void getAllMuscleGroups_shouldReturnEmptyListWhenNoMuscleGroupsExist() {
        // Given
        when(muscleGroupRepository.findAll()).thenReturn(new ArrayList<>());

        // When
        List<MuscleGroupDTO> result = readService.getAllMuscleGroups();

        // Then
        assertThat(result).isEmpty();
        verify(muscleGroupRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("getAllRoutines - should return all routines when isActive is null")
    void getAllRoutines_shouldReturnAllRoutinesWhenIsActiveIsNull() {
        // Given
        List<WorkoutRoutine> routines = Arrays.asList(activeRoutine, inactiveRoutine);
        when(workoutRoutineRepository.findAll()).thenReturn(routines);

        // When
        List<WorkoutRoutineDTO> result = readService.getAllRoutines(null);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("5-Day Split");
        assertThat(result.get(0).getIsActive()).isTrue();
        assertThat(result.get(1).getName()).isEqualTo("3-Day Split");
        assertThat(result.get(1).getIsActive()).isFalse();
        verify(workoutRoutineRepository, times(1)).findAll();
        verify(workoutRoutineRepository, never()).findByIsActive(any());
    }

    @Test
    @DisplayName("getAllRoutines - should return only active routines when isActive is true")
    void getAllRoutines_shouldReturnOnlyActiveRoutinesWhenIsActiveIsTrue() {
        // Given
        List<WorkoutRoutine> activeRoutines = Arrays.asList(activeRoutine);
        when(workoutRoutineRepository.findByIsActive(true)).thenReturn(activeRoutines);

        // When
        List<WorkoutRoutineDTO> result = readService.getAllRoutines(true);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("5-Day Split");
        assertThat(result.get(0).getIsActive()).isTrue();
        verify(workoutRoutineRepository, times(1)).findByIsActive(true);
        verify(workoutRoutineRepository, never()).findAll();
    }

    @Test
    @DisplayName("getAllRoutines - should return only inactive routines when isActive is false")
    void getAllRoutines_shouldReturnOnlyInactiveRoutinesWhenIsActiveIsFalse() {
        // Given
        List<WorkoutRoutine> inactiveRoutines = Arrays.asList(inactiveRoutine);
        when(workoutRoutineRepository.findByIsActive(false)).thenReturn(inactiveRoutines);

        // When
        List<WorkoutRoutineDTO> result = readService.getAllRoutines(false);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("3-Day Split");
        assertThat(result.get(0).getIsActive()).isFalse();
        verify(workoutRoutineRepository, times(1)).findByIsActive(false);
        verify(workoutRoutineRepository, never()).findAll();
    }

    @Test
    @DisplayName("getAllRoutines - should return empty list when no routines match criteria")
    void getAllRoutines_shouldReturnEmptyListWhenNoRoutinesMatchCriteria() {
        // Given
        when(workoutRoutineRepository.findByIsActive(true)).thenReturn(new ArrayList<>());

        // When
        List<WorkoutRoutineDTO> result = readService.getAllRoutines(true);

        // Then
        assertThat(result).isEmpty();
        verify(workoutRoutineRepository, times(1)).findByIsActive(true);
    }

    @Test
    @DisplayName("getRoutineById - should return Optional with RoutineDetailDTO for existing routine")
    void getRoutineById_shouldReturnOptionalWithRoutineDetailDTOForExistingRoutine() {
        // Given
        activeRoutine.getWorkoutDays().add(workoutDay1);
        activeRoutine.getWorkoutDays().add(workoutDay2);
        when(workoutRoutineRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(activeRoutine));

        // When
        Optional<WorkoutRoutineDetailDTO> result = readService.getRoutineById(1L);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(1L);
        assertThat(result.get().getName()).isEqualTo("5-Day Split");
        assertThat(result.get().getWorkoutDays()).hasSize(2);
        assertThat(result.get().getWorkoutDays().get(0).getDayName()).isEqualTo("Chest Day");
        assertThat(result.get().getWorkoutDays().get(1).getDayName()).isEqualTo("Back Day");
        verify(workoutRoutineRepository, times(1)).findByIdWithDetails(1L);
    }

    @Test
    @DisplayName("getRoutineById - should return empty Optional for non-existent routine")
    void getRoutineById_shouldReturnEmptyOptionalForNonExistentRoutine() {
        // Given
        when(workoutRoutineRepository.findByIdWithDetails(999L)).thenReturn(Optional.empty());

        // When
        Optional<WorkoutRoutineDetailDTO> result = readService.getRoutineById(999L);

        // Then
        assertThat(result).isEmpty();
        verify(workoutRoutineRepository, times(1)).findByIdWithDetails(999L);
    }

    @Test
    @DisplayName("getRoutineById - should return routine with empty workout days list")
    void getRoutineById_shouldReturnRoutineWithEmptyWorkoutDaysList() {
        // Given
        when(workoutRoutineRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(activeRoutine));

        // When
        Optional<WorkoutRoutineDetailDTO> result = readService.getRoutineById(1L);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getWorkoutDays()).isEmpty();
        verify(workoutRoutineRepository, times(1)).findByIdWithDetails(1L);
    }

    @Test
    @DisplayName("getWorkoutDaysByRoutineId - should return list of workout days for routine")
    void getWorkoutDaysByRoutineId_shouldReturnListOfWorkoutDaysForRoutine() {
        // Given
        List<WorkoutDay> workoutDays = Arrays.asList(workoutDay1, workoutDay2);
        when(workoutDayRepository.findByRoutineIdWithSets(1L)).thenReturn(workoutDays);

        // When
        List<WorkoutDayDetailDTO> result = readService.getWorkoutDaysByRoutineId(1L);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getDayNumber()).isEqualTo(1);
        assertThat(result.get(0).getDayName()).isEqualTo("Chest Day");
        assertThat(result.get(0).getRoutineId()).isEqualTo(1L);
        assertThat(result.get(1).getDayNumber()).isEqualTo(2);
        assertThat(result.get(1).getDayName()).isEqualTo("Back Day");
        verify(workoutDayRepository, times(1)).findByRoutineIdWithSets(1L);
    }

    @Test
    @DisplayName("getWorkoutDaysByRoutineId - should return empty list when routine has no days")
    void getWorkoutDaysByRoutineId_shouldReturnEmptyListWhenRoutineHasNoDays() {
        // Given
        when(workoutDayRepository.findByRoutineIdWithSets(1L)).thenReturn(new ArrayList<>());

        // When
        List<WorkoutDayDetailDTO> result = readService.getWorkoutDaysByRoutineId(1L);

        // Then
        assertThat(result).isEmpty();
        verify(workoutDayRepository, times(1)).findByRoutineIdWithSets(1L);
    }

    @Test
    @DisplayName("getWorkoutDaysByRoutineId - should return empty list for non-existent routine")
    void getWorkoutDaysByRoutineId_shouldReturnEmptyListForNonExistentRoutine() {
        // Given
        when(workoutDayRepository.findByRoutineIdWithSets(999L)).thenReturn(new ArrayList<>());

        // When
        List<WorkoutDayDetailDTO> result = readService.getWorkoutDaysByRoutineId(999L);

        // Then
        assertThat(result).isEmpty();
        verify(workoutDayRepository, times(1)).findByRoutineIdWithSets(999L);
    }
}