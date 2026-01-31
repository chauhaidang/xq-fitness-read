package com.xqfitness.readservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyReportDTO {

    private Integer routineId;
    private LocalDate weekStartDate;
    private Boolean hasSnapshot;
    private LocalDateTime snapshotCreatedAt;
    private List<MuscleGroupTotalDTO> muscleGroupTotals;
    private List<ExerciseTotalDTO> exerciseTotals;
}
