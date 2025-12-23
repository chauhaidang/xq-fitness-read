package com.xqfitness.readservice.service;

import com.xqfitness.readservice.dto.MuscleGroupDTO;
import com.xqfitness.readservice.entity.MuscleGroup;
import com.xqfitness.readservice.repository.MuscleGroupRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MuscleGroupService Unit Tests")
class MuscleGroupServiceTest {

    @Mock
    private MuscleGroupRepository muscleGroupRepository;

    @InjectMocks
    private ReadService readService;

    @Test
    @DisplayName("getAllMuscleGroups - should return Abductor muscle group (ID: 13)")
    void getAllMuscleGroups_shouldReturnAbductorMuscleGroup() {
        // Given
        MuscleGroup chest = new MuscleGroup();
        chest.setId(1);
        chest.setName("Chest");
        chest.setDescription("Chest muscles");
        chest.setCreatedAt(LocalDateTime.now());

        MuscleGroup abductor = new MuscleGroup();
        abductor.setId(13);
        abductor.setName("Abductor");
        abductor.setDescription("Hip abductor muscles (gluteus medius, gluteus minimus, tensor fasciae latae)");
        abductor.setCreatedAt(LocalDateTime.now());

        List<MuscleGroup> muscleGroups = Arrays.asList(chest, abductor);
        when(muscleGroupRepository.findAll()).thenReturn(muscleGroups);

        // When
        List<MuscleGroupDTO> result = readService.getAllMuscleGroups();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(MuscleGroupDTO::getId)
                .contains(13);
        assertThat(result).extracting(MuscleGroupDTO::getName)
                .contains("Abductor");
        assertThat(result).extracting(MuscleGroupDTO::getDescription)
                .contains("Hip abductor muscles (gluteus medius, gluteus minimus, tensor fasciae latae)");
        verify(muscleGroupRepository, times(1)).findAll();
    }
}

