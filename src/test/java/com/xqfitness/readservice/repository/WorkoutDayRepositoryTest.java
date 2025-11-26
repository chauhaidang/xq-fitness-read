package com.xqfitness.readservice.repository;

import com.xqfitness.readservice.entity.MuscleGroup;
import com.xqfitness.readservice.entity.WorkoutDay;
import com.xqfitness.readservice.entity.WorkoutDaySet;
import com.xqfitness.readservice.entity.WorkoutRoutine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("WorkoutDayRepository Integration Tests")
class WorkoutDayRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private WorkoutDayRepository workoutDayRepository;

    @Test
    @DisplayName("findByRoutineIdWithSets - should return workout days with sets and muscle groups")
    void findByRoutineIdWithSets_shouldReturnWorkoutDaysWithSetsAndMuscleGroups() {
        // Given
        WorkoutRoutine routine = createRoutine("5-Day Split", true);
        entityManager.persist(routine);

        MuscleGroup chest = createMuscleGroup("Chest", "Chest muscles");
        MuscleGroup back = createMuscleGroup("Back", "Back muscles");
        entityManager.persist(chest);
        entityManager.persist(back);

        WorkoutDay day1 = createWorkoutDay(routine, 1, "Chest Day");
        WorkoutDay day2 = createWorkoutDay(routine, 2, "Back Day");

        WorkoutDaySet set1 = createWorkoutDaySet(day1, chest, 4);
        WorkoutDaySet set2 = createWorkoutDaySet(day2, back, 5);

        day1.getSets().add(set1);
        day2.getSets().add(set2);

        entityManager.persist(day1);
        entityManager.persist(day2);
        entityManager.flush();
        entityManager.clear();

        // When
        List<WorkoutDay> days = workoutDayRepository.findByRoutineIdWithSets(routine.getId());

        // Then
        assertThat(days).hasSize(2);
        assertThat(days.get(0).getDayName()).isEqualTo("Chest Day");
        assertThat(days.get(0).getSets()).hasSize(1);
        assertThat(days.get(0).getSets().get(0).getMuscleGroup().getName()).isEqualTo("Chest");
        assertThat(days.get(1).getDayName()).isEqualTo("Back Day");
        assertThat(days.get(1).getSets()).hasSize(1);
        assertThat(days.get(1).getSets().get(0).getMuscleGroup().getName()).isEqualTo("Back");
    }

    @Test
    @DisplayName("findByRoutineIdWithSets - should return days ordered by dayNumber")
    void findByRoutineIdWithSets_shouldReturnDaysOrderedByDayNumber() {
        // Given
        WorkoutRoutine routine = createRoutine("Test Routine", true);
        entityManager.persist(routine);

        WorkoutDay day3 = createWorkoutDay(routine, 3, "Day Three");
        WorkoutDay day1 = createWorkoutDay(routine, 1, "Day One");
        WorkoutDay day2 = createWorkoutDay(routine, 2, "Day Two");

        entityManager.persist(day3);
        entityManager.persist(day1);
        entityManager.persist(day2);
        entityManager.flush();
        entityManager.clear();

        // When
        List<WorkoutDay> days = workoutDayRepository.findByRoutineIdWithSets(routine.getId());

        // Then
        assertThat(days).hasSize(3);
        assertThat(days.get(0).getDayNumber()).isEqualTo(1);
        assertThat(days.get(0).getDayName()).isEqualTo("Day One");
        assertThat(days.get(1).getDayNumber()).isEqualTo(2);
        assertThat(days.get(1).getDayName()).isEqualTo("Day Two");
        assertThat(days.get(2).getDayNumber()).isEqualTo(3);
        assertThat(days.get(2).getDayName()).isEqualTo("Day Three");
    }

    @Test
    @DisplayName("findByRoutineIdWithSets - should return empty list for routine with no days")
    void findByRoutineIdWithSets_shouldReturnEmptyListForRoutineWithNoDays() {
        // Given
        WorkoutRoutine routine = createRoutine("Empty Routine", true);
        entityManager.persist(routine);
        entityManager.flush();

        // When
        List<WorkoutDay> days = workoutDayRepository.findByRoutineIdWithSets(routine.getId());

        // Then
        assertThat(days).isEmpty();
    }

    @Test
    @DisplayName("findByRoutineIdWithSets - should return empty list for non-existent routine")
    void findByRoutineIdWithSets_shouldReturnEmptyListForNonExistentRoutine() {
        // When
        List<WorkoutDay> days = workoutDayRepository.findByRoutineIdWithSets(999);

        // Then
        assertThat(days).isEmpty();
    }

    @Test
    @DisplayName("findByRoutineIdWithSets - should handle workout day with multiple sets")
    void findByRoutineIdWithSets_shouldHandleWorkoutDayWithMultipleSets() {
        // Given
        WorkoutRoutine routine = createRoutine("Multi-Set Routine", true);
        entityManager.persist(routine);

        MuscleGroup chest = createMuscleGroup("Chest", "Chest muscles");
        MuscleGroup shoulders = createMuscleGroup("Shoulders", "Shoulder muscles");
        entityManager.persist(chest);
        entityManager.persist(shoulders);

        WorkoutDay day = createWorkoutDay(routine, 1, "Upper Body");

        WorkoutDaySet set1 = createWorkoutDaySet(day, chest, 4);
        WorkoutDaySet set2 = createWorkoutDaySet(day, shoulders, 3);

        day.getSets().add(set1);
        day.getSets().add(set2);

        entityManager.persist(day);
        entityManager.flush();
        entityManager.clear();

        // When
        List<WorkoutDay> days = workoutDayRepository.findByRoutineIdWithSets(routine.getId());

        // Then
        assertThat(days).hasSize(1);
        assertThat(days.get(0).getSets()).hasSize(2);
        assertThat(days.get(0).getSets())
                .extracting(set -> set.getMuscleGroup().getName())
                .containsExactlyInAnyOrder("Chest", "Shoulders");
    }

    @Test
    @DisplayName("findByRoutineIdWithSets - should return workout days with empty sets list")
    void findByRoutineIdWithSets_shouldReturnWorkoutDaysWithEmptySetsList() {
        // Given
        WorkoutRoutine routine = createRoutine("No Sets Routine", true);
        entityManager.persist(routine);

        WorkoutDay day = createWorkoutDay(routine, 1, "Empty Day");
        entityManager.persist(day);
        entityManager.flush();
        entityManager.clear();

        // When
        List<WorkoutDay> days = workoutDayRepository.findByRoutineIdWithSets(routine.getId());

        // Then
        assertThat(days).hasSize(1);
        assertThat(days.get(0).getSets()).isEmpty();
    }

    @Test
    @DisplayName("save - should persist workout day with timestamps")
    void save_shouldPersistWorkoutDayWithTimestamps() {
        // Given
        WorkoutRoutine routine = createRoutine("Test Routine", true);
        entityManager.persist(routine);

        WorkoutDay day = createWorkoutDay(routine, 1, "Test Day");

        // When
        WorkoutDay saved = workoutDayRepository.save(day);
        entityManager.flush();
        entityManager.clear();

        // Then
        WorkoutDay found = workoutDayRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    private WorkoutRoutine createRoutine(String name, boolean isActive) {
        WorkoutRoutine routine = new WorkoutRoutine();
        routine.setName(name);
        routine.setDescription("Description for " + name);
        routine.setIsActive(isActive);
        return routine;
    }

    private MuscleGroup createMuscleGroup(String name, String description) {
        MuscleGroup muscleGroup = new MuscleGroup();
        muscleGroup.setName(name);
        muscleGroup.setDescription(description);
        return muscleGroup;
    }

    private WorkoutDay createWorkoutDay(WorkoutRoutine routine, int dayNumber, String dayName) {
        WorkoutDay day = new WorkoutDay();
        day.setRoutine(routine);
        day.setDayNumber(dayNumber);
        day.setDayName(dayName);
        day.setNotes("Notes for " + dayName);
        return day;
    }

    private WorkoutDaySet createWorkoutDaySet(WorkoutDay workoutDay, MuscleGroup muscleGroup, int numberOfSets) {
        WorkoutDaySet set = new WorkoutDaySet();
        set.setWorkoutDay(workoutDay);
        set.setMuscleGroup(muscleGroup);
        set.setNumberOfSets(numberOfSets);
        set.setNotes("Set notes");
        return set;
    }
}
