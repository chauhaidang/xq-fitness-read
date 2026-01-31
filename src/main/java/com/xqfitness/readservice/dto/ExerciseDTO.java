package com.xqfitness.readservice.dto;

import com.xqfitness.readservice.entity.Exercise;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public static List<ExerciseDTO> fromEntities(List<Exercise> entities) {
        return entities.stream()
                .map(ExerciseDTO::fromEntity)
                .collect(Collectors.toList());
    }
}
