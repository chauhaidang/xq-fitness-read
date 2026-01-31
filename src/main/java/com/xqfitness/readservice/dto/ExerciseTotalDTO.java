package com.xqfitness.readservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseTotalDTO {

    private String exerciseName;
    private MuscleGroupDTO muscleGroup;
    private Integer totalReps;
    private BigDecimal totalWeight;
}
