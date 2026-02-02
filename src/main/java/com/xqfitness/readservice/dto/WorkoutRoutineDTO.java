package com.xqfitness.readservice.dto;

import com.xqfitness.readservice.entity.WorkoutRoutine;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkoutRoutineDTO {
    private Integer id;
    private String name;
    private String description;
    private Boolean isActive;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static WorkoutRoutineDTO fromEntity(WorkoutRoutine entity) {
        return new WorkoutRoutineDTO(
            entity.getId(),
            entity.getName(),
            entity.getDescription(),
            entity.getIsActive(),
            entity.getCreatedAt() != null ? entity.getCreatedAt().atOffset(ZoneOffset.UTC) : null,
            entity.getUpdatedAt() != null ? entity.getUpdatedAt().atOffset(ZoneOffset.UTC) : null
        );
    }
}
