package com.xqfitness.readservice.dto;

import com.xqfitness.readservice.entity.WorkoutRoutine;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkoutRoutineDetailDTO {
    private Long id;
    private String name;
    private String description;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<WorkoutDayDetailDTO> workoutDays;

    public static WorkoutRoutineDetailDTO fromEntity(WorkoutRoutine entity) {
        return new WorkoutRoutineDetailDTO(
            entity.getId(),
            entity.getName(),
            entity.getDescription(),
            entity.getIsActive(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getWorkoutDays().stream()
                .map(WorkoutDayDetailDTO::fromEntity)
                .collect(Collectors.toList())
        );
    }
}
