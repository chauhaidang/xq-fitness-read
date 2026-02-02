package com.xqfitness.readservice.dto;

import com.xqfitness.readservice.entity.Exercise;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseDTO {
    private Integer id;
    private Integer workoutDayId;
    private Integer muscleGroupId;
    private String exerciseName;
    private MuscleGroupDTO muscleGroup;
    private Integer totalReps;
    private BigDecimal weight;
    private Integer totalSets;
    private String notes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static ExerciseDTO fromEntity(Exercise entity) {
        return new ExerciseDTO(
                entity.getId(),
                entity.getWorkoutDayId(),
                entity.getMuscleGroup() != null ? entity.getMuscleGroup().getId() : null,
                entity.getExerciseName(),
                entity.getMuscleGroup() != null ? MuscleGroupDTO.fromEntity(entity.getMuscleGroup()) : null,
                entity.getTotalReps(),
                entity.getWeight(),
                entity.getTotalSets(),
                entity.getNotes(),
                entity.getCreatedAt() != null ? entity.getCreatedAt().atOffset(ZoneOffset.UTC) : null,
                entity.getUpdatedAt() != null ? entity.getUpdatedAt().atOffset(ZoneOffset.UTC) : null
        );
    }

    public static List<ExerciseDTO> fromEntities(List<Exercise> entities) {
        return entities.stream()
                .map(ExerciseDTO::fromEntity)
                .collect(Collectors.toList());
    }
}
