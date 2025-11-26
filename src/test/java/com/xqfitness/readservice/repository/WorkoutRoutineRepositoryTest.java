package com.xqfitness.readservice.repository;

import com.xqfitness.readservice.entity.WorkoutDay;
import com.xqfitness.readservice.entity.WorkoutRoutine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("WorkoutRoutineRepository Integration Tests")
class WorkoutRoutineRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private WorkoutRoutineRepository workoutRoutineRepository;

    @Test
    @DisplayName("findAll - should return all workout routines")
    void findAll_shouldReturnAllWorkoutRoutines() {
        // Given
        WorkoutRoutine routine1 = createRoutine("5-Day Split", "5 day split", true);
        WorkoutRoutine routine2 = createRoutine("3-Day Split", "3 day split", false);
        entityManager.persist(routine1);
        entityManager.persist(routine2);
        entityManager.flush();

        // When
        List<WorkoutRoutine> routines = workoutRoutineRepository.findAll();

        // Then
        assertThat(routines).hasSize(2);
        assertThat(routines).extracting(WorkoutRoutine::getName)
                .containsExactlyInAnyOrder("5-Day Split", "3-Day Split");
    }

    @Test
    @DisplayName("findByIsActive - should return only active routines")
    void findByIsActive_shouldReturnOnlyActiveRoutines() {
        // Given
        WorkoutRoutine activeRoutine = createRoutine("Active Routine", "Active", true);
        WorkoutRoutine inactiveRoutine = createRoutine("Inactive Routine", "Inactive", false);
        entityManager.persist(activeRoutine);
        entityManager.persist(inactiveRoutine);
        entityManager.flush();

        // When
        List<WorkoutRoutine> activeRoutines = workoutRoutineRepository.findByIsActive(true);

        // Then
        assertThat(activeRoutines).hasSize(1);
        assertThat(activeRoutines.get(0).getName()).isEqualTo("Active Routine");
        assertThat(activeRoutines.get(0).getIsActive()).isTrue();
    }

    @Test
    @DisplayName("findByIsActive - should return only inactive routines")
    void findByIsActive_shouldReturnOnlyInactiveRoutines() {
        // Given
        WorkoutRoutine activeRoutine = createRoutine("Active Routine", "Active", true);
        WorkoutRoutine inactiveRoutine = createRoutine("Inactive Routine", "Inactive", false);
        entityManager.persist(activeRoutine);
        entityManager.persist(inactiveRoutine);
        entityManager.flush();

        // When
        List<WorkoutRoutine> inactiveRoutines = workoutRoutineRepository.findByIsActive(false);

        // Then
        assertThat(inactiveRoutines).hasSize(1);
        assertThat(inactiveRoutines.get(0).getName()).isEqualTo("Inactive Routine");
        assertThat(inactiveRoutines.get(0).getIsActive()).isFalse();
    }

    @Test
    @DisplayName("findByIsActive - should return empty list when no matching routines exist")
    void findByIsActive_shouldReturnEmptyListWhenNoMatchingRoutinesExist() {
        // Given
        WorkoutRoutine activeRoutine = createRoutine("Active Routine", "Active", true);
        entityManager.persist(activeRoutine);
        entityManager.flush();

        // When
        List<WorkoutRoutine> inactiveRoutines = workoutRoutineRepository.findByIsActive(false);

        // Then
        assertThat(inactiveRoutines).isEmpty();
    }

    @Test
    @DisplayName("findByIdWithDetails - should return routine with workout days eagerly loaded")
    void findByIdWithDetails_shouldReturnRoutineWithWorkoutDaysEagerlyLoaded() {
        // Given
        WorkoutRoutine routine = createRoutine("Full Routine", "Complete routine", true);

        WorkoutDay day1 = new WorkoutDay();
        day1.setRoutine(routine);
        day1.setDayNumber(1);
        day1.setDayName("Chest Day");
        day1.setNotes("Chest exercises");

        WorkoutDay day2 = new WorkoutDay();
        day2.setRoutine(routine);
        day2.setDayNumber(2);
        day2.setDayName("Back Day");
        day2.setNotes("Back exercises");

        routine.getWorkoutDays().add(day1);
        routine.getWorkoutDays().add(day2);

        entityManager.persist(routine);
        entityManager.flush();
        entityManager.clear();

        // When
        Optional<WorkoutRoutine> found = workoutRoutineRepository.findByIdWithDetails(routine.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Full Routine");
        assertThat(found.get().getWorkoutDays()).hasSize(2);
        assertThat(found.get().getWorkoutDays()).extracting(WorkoutDay::getDayName)
                .containsExactlyInAnyOrder("Chest Day", "Back Day");
    }

    @Test
    @DisplayName("findByIdWithDetails - should return empty Optional for non-existent routine")
    void findByIdWithDetails_shouldReturnEmptyOptionalForNonExistentRoutine() {
        // When
        Optional<WorkoutRoutine> found = workoutRoutineRepository.findByIdWithDetails(999);

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findByIdWithDetails - should return routine with empty workout days list")
    void findByIdWithDetails_shouldReturnRoutineWithEmptyWorkoutDaysList() {
        // Given
        WorkoutRoutine routine = createRoutine("Empty Routine", "No days", true);
        entityManager.persist(routine);
        entityManager.flush();
        entityManager.clear();

        // When
        Optional<WorkoutRoutine> found = workoutRoutineRepository.findByIdWithDetails(routine.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getWorkoutDays()).isEmpty();
    }

    @Test
    @DisplayName("save - should persist routine with timestamps")
    void save_shouldPersistRoutineWithTimestamps() {
        // Given
        WorkoutRoutine routine = createRoutine("New Routine", "New", true);

        // When
        WorkoutRoutine saved = workoutRoutineRepository.save(routine);
        entityManager.flush();
        entityManager.clear();

        // Then
        WorkoutRoutine found = workoutRoutineRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    private WorkoutRoutine createRoutine(String name, String description, boolean isActive) {
        WorkoutRoutine routine = new WorkoutRoutine();
        routine.setName(name);
        routine.setDescription(description);
        routine.setIsActive(isActive);
        return routine;
    }
}