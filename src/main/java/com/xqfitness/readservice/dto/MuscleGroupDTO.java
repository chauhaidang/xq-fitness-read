package com.xqfitness.readservice.dto;

import com.xqfitness.readservice.entity.MuscleGroup;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MuscleGroupDTO {
    private Long id;
    private String name;
    private String description;
    private LocalDateTime createdAt;

    public static MuscleGroupDTO fromEntity(MuscleGroup entity) {
        return new MuscleGroupDTO(
            entity.getId(),
            entity.getName(),
            entity.getDescription(),
            entity.getCreatedAt()
        );
    }
}
