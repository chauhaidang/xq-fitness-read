package com.xqfitness.readservice.dto;

import com.xqfitness.readservice.entity.MuscleGroup;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MuscleGroupDTO {
    private Integer id;
    private String name;
    private String description;
    private OffsetDateTime createdAt;

    public static MuscleGroupDTO fromEntity(MuscleGroup entity) {
        return new MuscleGroupDTO(
            entity.getId(),
            entity.getName(),
            entity.getDescription(),
            entity.getCreatedAt() != null ? entity.getCreatedAt().atOffset(ZoneOffset.UTC) : null
        );
    }
}
