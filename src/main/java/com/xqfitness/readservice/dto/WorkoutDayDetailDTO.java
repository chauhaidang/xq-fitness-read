package com.xqfitness.readservice.dto;

import com.xqfitness.readservice.entity.Exercise;
import com.xqfitness.readservice.entity.WorkoutDay;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkoutDayDetailDTO {
    private Integer id;
    private Integer routineId;
    private Integer dayNumber;
    private String dayName;
    private String notes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<WorkoutDaySetDTO> sets;
    private List<ExerciseDTO> exercises;

    public static WorkoutDayDetailDTO fromEntity(WorkoutDay entity) {
        return new WorkoutDayDetailDTO(
            entity.getId(),
            entity.getRoutine().getId(),
            entity.getDayNumber(),
            entity.getDayName(),
            entity.getNotes(),
            entity.getCreatedAt() != null ? entity.getCreatedAt().atOffset(ZoneOffset.UTC) : null,
            entity.getUpdatedAt() != null ? entity.getUpdatedAt().atOffset(ZoneOffset.UTC) : null,
            entity.getSets().stream()
                .map(WorkoutDaySetDTO::fromEntity)
                .collect(Collectors.toList()),
            Collections.emptyList()
        );
    }

    public static WorkoutDayDetailDTO fromEntity(WorkoutDay entity, List<Exercise> exercises) {
        return new WorkoutDayDetailDTO(
            entity.getId(),
            entity.getRoutine().getId(),
            entity.getDayNumber(),
            entity.getDayName(),
            entity.getNotes(),
            entity.getCreatedAt() != null ? entity.getCreatedAt().atOffset(ZoneOffset.UTC) : null,
            entity.getUpdatedAt() != null ? entity.getUpdatedAt().atOffset(ZoneOffset.UTC) : null,
            entity.getSets().stream()
                .map(WorkoutDaySetDTO::fromEntity)
                .collect(Collectors.toList()),
            ExerciseDTO.fromEntities(exercises)
        );
    }
}
