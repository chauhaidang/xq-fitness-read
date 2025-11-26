package com.xqfitness.readservice.dto;

import com.xqfitness.readservice.entity.MuscleGroup;
import com.xqfitness.readservice.entity.WorkoutDay;
import com.xqfitness.readservice.entity.WorkoutDaySet;
import com.xqfitness.readservice.entity.WorkoutRoutine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DTO Conversion Unit Tests")
class DTOConversionTest {

    private LocalDateTime now;
    private MuscleGroup muscleGroupEntity;
    private WorkoutRoutine workoutRoutineEntity;
    private WorkoutDay workoutDayEntity;
    private WorkoutDaySet workoutDaySetEntity;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now();

        // Setup MuscleGroup entity
        muscleGroupEntity = new MuscleGroup();
        muscleGroupEntity.setId(1);
        muscleGroupEntity.setName("Chest");
        muscleGroupEntity.setDescription("Chest muscles");
        muscleGroupEntity.setCreatedAt(now);

        // Setup WorkoutRoutine entity
        workoutRoutineEntity = new WorkoutRoutine();
        workoutRoutineEntity.setId(1);
        workoutRoutineEntity.setName("5-Day Split");
        workoutRoutineEntity.setDescription("5 day workout split");
        workoutRoutineEntity.setIsActive(true);
        workoutRoutineEntity.setCreatedAt(now);
        workoutRoutineEntity.setUpdatedAt(now);
        workoutRoutineEntity.setWorkoutDays(new ArrayList<>());

        // Setup WorkoutDay entity
        workoutDayEntity = new WorkoutDay();
        workoutDayEntity.setId(1);
        workoutDayEntity.setRoutine(workoutRoutineEntity);
        workoutDayEntity.setDayNumber(1);
        workoutDayEntity.setDayName("Chest Day");
        workoutDayEntity.setNotes("Focus on compound movements");
        workoutDayEntity.setCreatedAt(now);
        workoutDayEntity.setUpdatedAt(now);
        workoutDayEntity.setSets(new ArrayList<>());

        // Setup WorkoutDaySet entity
        workoutDaySetEntity = new WorkoutDaySet();
        workoutDaySetEntity.setId(1);
        workoutDaySetEntity.setWorkoutDay(workoutDayEntity);
        workoutDaySetEntity.setMuscleGroup(muscleGroupEntity);
        workoutDaySetEntity.setNumberOfSets(4);
        workoutDaySetEntity.setNotes("Bench press and variations");
        workoutDaySetEntity.setCreatedAt(now);
        workoutDaySetEntity.setUpdatedAt(now);
    }

    @Test
    @DisplayName("MuscleGroupDTO.fromEntity - should convert all fields correctly")
    void muscleGroupDTOFromEntity_shouldConvertAllFieldsCorrectly() {
        // When
        MuscleGroupDTO dto = MuscleGroupDTO.fromEntity(muscleGroupEntity);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(1);
        assertThat(dto.getName()).isEqualTo("Chest");
        assertThat(dto.getDescription()).isEqualTo("Chest muscles");
        assertThat(dto.getCreatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("MuscleGroupDTO.fromEntity - should handle null description")
    void muscleGroupDTOFromEntity_shouldHandleNullDescription() {
        // Given
        muscleGroupEntity.setDescription(null);

        // When
        MuscleGroupDTO dto = MuscleGroupDTO.fromEntity(muscleGroupEntity);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.getDescription()).isNull();
    }

    @Test
    @DisplayName("WorkoutRoutineDTO.fromEntity - should convert all fields correctly")
    void workoutRoutineDTOFromEntity_shouldConvertAllFieldsCorrectly() {
        // When
        WorkoutRoutineDTO dto = WorkoutRoutineDTO.fromEntity(workoutRoutineEntity);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(1);
        assertThat(dto.getName()).isEqualTo("5-Day Split");
        assertThat(dto.getDescription()).isEqualTo("5 day workout split");
        assertThat(dto.getIsActive()).isTrue();
        assertThat(dto.getCreatedAt()).isEqualTo(now);
        assertThat(dto.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("WorkoutRoutineDTO.fromEntity - should handle inactive routine")
    void workoutRoutineDTOFromEntity_shouldHandleInactiveRoutine() {
        // Given
        workoutRoutineEntity.setIsActive(false);

        // When
        WorkoutRoutineDTO dto = WorkoutRoutineDTO.fromEntity(workoutRoutineEntity);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("WorkoutRoutineDetailDTO.fromEntity - should convert routine with workout days")
    void workoutRoutineDetailDTOFromEntity_shouldConvertRoutineWithWorkoutDays() {
        // Given
        WorkoutDay day1 = new WorkoutDay();
        day1.setId(1);
        day1.setRoutine(workoutRoutineEntity);
        day1.setDayNumber(1);
        day1.setDayName("Chest Day");
        day1.setNotes("Notes 1");
        day1.setCreatedAt(now);
        day1.setUpdatedAt(now);
        day1.setSets(new ArrayList<>());

        WorkoutDay day2 = new WorkoutDay();
        day2.setId(2);
        day2.setRoutine(workoutRoutineEntity);
        day2.setDayNumber(2);
        day2.setDayName("Back Day");
        day2.setNotes("Notes 2");
        day2.setCreatedAt(now);
        day2.setUpdatedAt(now);
        day2.setSets(new ArrayList<>());

        workoutRoutineEntity.setWorkoutDays(Arrays.asList(day1, day2));

        // When
        WorkoutRoutineDetailDTO dto = WorkoutRoutineDetailDTO.fromEntity(workoutRoutineEntity);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(1);
        assertThat(dto.getName()).isEqualTo("5-Day Split");
        assertThat(dto.getWorkoutDays()).hasSize(2);
        assertThat(dto.getWorkoutDays().get(0).getDayName()).isEqualTo("Chest Day");
        assertThat(dto.getWorkoutDays().get(1).getDayName()).isEqualTo("Back Day");
    }

    @Test
    @DisplayName("WorkoutRoutineDetailDTO.fromEntity - should handle empty workout days list")
    void workoutRoutineDetailDTOFromEntity_shouldHandleEmptyWorkoutDaysList() {
        // When
        WorkoutRoutineDetailDTO dto = WorkoutRoutineDetailDTO.fromEntity(workoutRoutineEntity);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.getWorkoutDays()).isEmpty();
    }

    @Test
    @DisplayName("WorkoutDayDetailDTO.fromEntity - should convert all fields correctly")
    void workoutDayDetailDTOFromEntity_shouldConvertAllFieldsCorrectly() {
        // When
        WorkoutDayDetailDTO dto = WorkoutDayDetailDTO.fromEntity(workoutDayEntity);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(1);
        assertThat(dto.getRoutineId()).isEqualTo(1);
        assertThat(dto.getDayNumber()).isEqualTo(1);
        assertThat(dto.getDayName()).isEqualTo("Chest Day");
        assertThat(dto.getNotes()).isEqualTo("Focus on compound movements");
        assertThat(dto.getCreatedAt()).isEqualTo(now);
        assertThat(dto.getUpdatedAt()).isEqualTo(now);
        assertThat(dto.getSets()).isEmpty();
    }

    @Test
    @DisplayName("WorkoutDayDetailDTO.fromEntity - should convert workout day with sets")
    void workoutDayDetailDTOFromEntity_shouldConvertWorkoutDayWithSets() {
        // Given
        WorkoutDaySet set1 = new WorkoutDaySet();
        set1.setId(1);
        set1.setWorkoutDay(workoutDayEntity);
        set1.setMuscleGroup(muscleGroupEntity);
        set1.setNumberOfSets(4);
        set1.setNotes("Set notes 1");
        set1.setCreatedAt(now);
        set1.setUpdatedAt(now);

        MuscleGroup back = new MuscleGroup();
        back.setId(2);
        back.setName("Back");
        back.setDescription("Back muscles");
        back.setCreatedAt(now);

        WorkoutDaySet set2 = new WorkoutDaySet();
        set2.setId(2);
        set2.setWorkoutDay(workoutDayEntity);
        set2.setMuscleGroup(back);
        set2.setNumberOfSets(3);
        set2.setNotes("Set notes 2");
        set2.setCreatedAt(now);
        set2.setUpdatedAt(now);

        workoutDayEntity.setSets(Arrays.asList(set1, set2));

        // When
        WorkoutDayDetailDTO dto = WorkoutDayDetailDTO.fromEntity(workoutDayEntity);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.getSets()).hasSize(2);
        assertThat(dto.getSets().get(0).getNumberOfSets()).isEqualTo(4);
        assertThat(dto.getSets().get(0).getMuscleGroup().getName()).isEqualTo("Chest");
        assertThat(dto.getSets().get(1).getNumberOfSets()).isEqualTo(3);
        assertThat(dto.getSets().get(1).getMuscleGroup().getName()).isEqualTo("Back");
    }

    @Test
    @DisplayName("WorkoutDayDetailDTO.fromEntity - should extract routineId from nested routine")
    void workoutDayDetailDTOFromEntity_shouldExtractRoutineIdFromNestedRoutine() {
        // When
        WorkoutDayDetailDTO dto = WorkoutDayDetailDTO.fromEntity(workoutDayEntity);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.getRoutineId()).isEqualTo(workoutRoutineEntity.getId());
    }

    @Test
    @DisplayName("WorkoutDaySetDTO.fromEntity - should convert all fields correctly")
    void workoutDaySetDTOFromEntity_shouldConvertAllFieldsCorrectly() {
        // When
        WorkoutDaySetDTO dto = WorkoutDaySetDTO.fromEntity(workoutDaySetEntity);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(1);
        assertThat(dto.getWorkoutDayId()).isEqualTo(1);
        assertThat(dto.getNumberOfSets()).isEqualTo(4);
        assertThat(dto.getNotes()).isEqualTo("Bench press and variations");
        assertThat(dto.getCreatedAt()).isEqualTo(now);
        assertThat(dto.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("WorkoutDaySetDTO.fromEntity - should convert nested MuscleGroupDTO")
    void workoutDaySetDTOFromEntity_shouldConvertNestedMuscleGroupDTO() {
        // When
        WorkoutDaySetDTO dto = WorkoutDaySetDTO.fromEntity(workoutDaySetEntity);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.getMuscleGroup()).isNotNull();
        assertThat(dto.getMuscleGroup().getId()).isEqualTo(1);
        assertThat(dto.getMuscleGroup().getName()).isEqualTo("Chest");
        assertThat(dto.getMuscleGroup().getDescription()).isEqualTo("Chest muscles");
    }

    @Test
    @DisplayName("WorkoutDaySetDTO.fromEntity - should extract workoutDayId from nested workoutDay")
    void workoutDaySetDTOFromEntity_shouldExtractWorkoutDayIdFromNestedWorkoutDay() {
        // When
        WorkoutDaySetDTO dto = WorkoutDaySetDTO.fromEntity(workoutDaySetEntity);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.getWorkoutDayId()).isEqualTo(workoutDayEntity.getId());
    }

    @Test
    @DisplayName("ErrorDTO - constructor should set timestamp automatically")
    void errorDTO_constructorShouldSetTimestampAutomatically() {
        // Given
        LocalDateTime beforeCreation = LocalDateTime.now().minusSeconds(1);

        // When
        ErrorDTO dto = new ErrorDTO("ERROR_CODE", "Error message");
        LocalDateTime afterCreation = LocalDateTime.now().plusSeconds(1);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.getCode()).isEqualTo("ERROR_CODE");
        assertThat(dto.getMessage()).isEqualTo("Error message");
        assertThat(dto.getTimestamp()).isNotNull();
        assertThat(dto.getTimestamp()).isBetween(beforeCreation, afterCreation);
    }

    @Test
    @DisplayName("ErrorDTO - should handle null message")
    void errorDTO_shouldHandleNullMessage() {
        // When
        ErrorDTO dto = new ErrorDTO("ERROR_CODE", null);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.getCode()).isEqualTo("ERROR_CODE");
        assertThat(dto.getMessage()).isNull();
        assertThat(dto.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("Complex DTO conversion - should convert full entity graph")
    void complexDTOConversion_shouldConvertFullEntityGraph() {
        // Given - Build a complete entity graph
        MuscleGroup chest = muscleGroupEntity;
        MuscleGroup shoulders = new MuscleGroup();
        shoulders.setId(2);
        shoulders.setName("Shoulders");
        shoulders.setDescription("Shoulder muscles");
        shoulders.setCreatedAt(now);

        WorkoutRoutine routine = workoutRoutineEntity;

        WorkoutDay day1 = new WorkoutDay();
        day1.setId(1);
        day1.setRoutine(routine);
        day1.setDayNumber(1);
        day1.setDayName("Upper Body");
        day1.setNotes("Upper body workout");
        day1.setCreatedAt(now);
        day1.setUpdatedAt(now);

        WorkoutDaySet set1 = new WorkoutDaySet();
        set1.setId(1);
        set1.setWorkoutDay(day1);
        set1.setMuscleGroup(chest);
        set1.setNumberOfSets(4);
        set1.setNotes("Chest exercises");
        set1.setCreatedAt(now);
        set1.setUpdatedAt(now);

        WorkoutDaySet set2 = new WorkoutDaySet();
        set2.setId(2);
        set2.setWorkoutDay(day1);
        set2.setMuscleGroup(shoulders);
        set2.setNumberOfSets(3);
        set2.setNotes("Shoulder exercises");
        set2.setCreatedAt(now);
        set2.setUpdatedAt(now);

        day1.setSets(Arrays.asList(set1, set2));
        routine.setWorkoutDays(Arrays.asList(day1));

        // When
        WorkoutRoutineDetailDTO dto = WorkoutRoutineDetailDTO.fromEntity(routine);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(1);
        assertThat(dto.getName()).isEqualTo("5-Day Split");
        assertThat(dto.getWorkoutDays()).hasSize(1);

        WorkoutDayDetailDTO dayDTO = dto.getWorkoutDays().get(0);
        assertThat(dayDTO.getDayName()).isEqualTo("Upper Body");
        assertThat(dayDTO.getSets()).hasSize(2);

        WorkoutDaySetDTO setDTO1 = dayDTO.getSets().get(0);
        assertThat(setDTO1.getMuscleGroup().getName()).isEqualTo("Chest");
        assertThat(setDTO1.getNumberOfSets()).isEqualTo(4);

        WorkoutDaySetDTO setDTO2 = dayDTO.getSets().get(1);
        assertThat(setDTO2.getMuscleGroup().getName()).isEqualTo("Shoulders");
        assertThat(setDTO2.getNumberOfSets()).isEqualTo(3);
    }
}
