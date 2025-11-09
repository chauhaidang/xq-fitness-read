package com.xqfitness.readservice.dto;

import com.xqfitness.readservice.entity.WorkoutRoutine;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkoutRoutineDTO {
    private Long id;
    private String name;
    private String description;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static WorkoutRoutineDTO fromEntity(WorkoutRoutine entity) {
        return new WorkoutRoutineDTO(
            entity.getId(),
            entity.getName(),
            entity.getDescription(),
            entity.getIsActive(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
