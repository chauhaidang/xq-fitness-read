package com.xqfitness.readservice.dto;

import com.xqfitness.readservice.entity.WorkoutDay;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkoutDayDetailDTO {
    private Long id;
    private Long routineId;
    private Integer dayNumber;
    private String dayName;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<WorkoutDaySetDTO> sets;

    public static WorkoutDayDetailDTO fromEntity(WorkoutDay entity) {
        return new WorkoutDayDetailDTO(
            entity.getId(),
            entity.getRoutine().getId(),
            entity.getDayNumber(),
            entity.getDayName(),
            entity.getNotes(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getSets().stream()
                .map(WorkoutDaySetDTO::fromEntity)
                .collect(Collectors.toList())
        );
    }
}
