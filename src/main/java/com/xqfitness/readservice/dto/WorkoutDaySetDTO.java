package com.xqfitness.readservice.dto;

import com.xqfitness.readservice.entity.WorkoutDaySet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkoutDaySetDTO {
    private Integer id;
    private Integer workoutDayId;
    private MuscleGroupDTO muscleGroup;
    private Integer numberOfSets;
    private String notes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static WorkoutDaySetDTO fromEntity(WorkoutDaySet entity) {
        return new WorkoutDaySetDTO(
            entity.getId(),
            entity.getWorkoutDay().getId(),
            MuscleGroupDTO.fromEntity(entity.getMuscleGroup()),
            entity.getNumberOfSets(),
            entity.getNotes(),
            entity.getCreatedAt() != null ? entity.getCreatedAt().atOffset(ZoneOffset.UTC) : null,
            entity.getUpdatedAt() != null ? entity.getUpdatedAt().atOffset(ZoneOffset.UTC) : null
        );
    }
}
