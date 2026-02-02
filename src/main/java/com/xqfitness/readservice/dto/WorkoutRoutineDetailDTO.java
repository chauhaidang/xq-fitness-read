package com.xqfitness.readservice.dto;

import com.xqfitness.readservice.entity.WorkoutRoutine;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkoutRoutineDetailDTO {
    private Integer id;
    private String name;
    private String description;
    private Boolean isActive;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<WorkoutDayDetailDTO> workoutDays;

    public static WorkoutRoutineDetailDTO fromEntity(WorkoutRoutine entity) {
        return new WorkoutRoutineDetailDTO(
            entity.getId(),
            entity.getName(),
            entity.getDescription(),
            entity.getIsActive(),
            entity.getCreatedAt() != null ? entity.getCreatedAt().atOffset(ZoneOffset.UTC) : null,
            entity.getUpdatedAt() != null ? entity.getUpdatedAt().atOffset(ZoneOffset.UTC) : null,
            entity.getWorkoutDays().stream()
                .map(WorkoutDayDetailDTO::fromEntity)
                .collect(Collectors.toList())
        );
    }
}
