package com.xqfitness.readservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MuscleGroupTotalDTO {

    private MuscleGroupDTO muscleGroup;
    private Integer totalSets;
}
