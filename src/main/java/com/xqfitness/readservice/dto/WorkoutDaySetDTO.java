package com.xqfitness.readservice.dto;

import com.xqfitness.readservice.entity.WorkoutDaySet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkoutDaySetDTO {
    private Long id;
    private Long workoutDayId;
    private MuscleGroupDTO muscleGroup;
    private Integer numberOfSets;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static WorkoutDaySetDTO fromEntity(WorkoutDaySet entity) {
        return new WorkoutDaySetDTO(
            entity.getId(),
            entity.getWorkoutDay().getId(),
            MuscleGroupDTO.fromEntity(entity.getMuscleGroup()),
            entity.getNumberOfSets(),
            entity.getNotes(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
