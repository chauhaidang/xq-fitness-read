package com.xqfitness.readservice.repository;

import com.xqfitness.readservice.entity.MuscleGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("MuscleGroupRepository Integration Tests")
class MuscleGroupRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MuscleGroupRepository muscleGroupRepository;

    @Test
    @DisplayName("findAll - should return all muscle groups")
    void findAll_shouldReturnAllMuscleGroups() {
        // Given
        MuscleGroup chest = new MuscleGroup();
        chest.setName("Chest");
        chest.setDescription("Chest muscles");
        entityManager.persist(chest);

        MuscleGroup back = new MuscleGroup();
        back.setName("Back");
        back.setDescription("Back muscles");
        entityManager.persist(back);

        entityManager.flush();

        // When
        List<MuscleGroup> muscleGroups = muscleGroupRepository.findAll();

        // Then
        assertThat(muscleGroups).hasSize(2);
        assertThat(muscleGroups).extracting(MuscleGroup::getName)
                .containsExactlyInAnyOrder("Chest", "Back");
    }

    @Test
    @DisplayName("findById - should return muscle group for existing ID")
    void findById_shouldReturnMuscleGroupForExistingId() {
        // Given
        MuscleGroup chest = new MuscleGroup();
        chest.setName("Chest");
        chest.setDescription("Chest muscles");
        MuscleGroup saved = entityManager.persist(chest);
        entityManager.flush();

        // When
        Optional<MuscleGroup> found = muscleGroupRepository.findById(saved.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Chest");
        assertThat(found.get().getDescription()).isEqualTo("Chest muscles");
    }

    @Test
    @DisplayName("findById - should return empty Optional for non-existent ID")
    void findById_shouldReturnEmptyOptionalForNonExistentId() {
        // When
        Optional<MuscleGroup> found = muscleGroupRepository.findById(999);

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("save - should persist muscle group with createdAt timestamp")
    void save_shouldPersistMuscleGroupWithCreatedAtTimestamp() {
        // Given
        MuscleGroup legs = new MuscleGroup();
        legs.setName("Legs");
        legs.setDescription("Leg muscles");

        // When
        MuscleGroup saved = muscleGroupRepository.save(legs);
        entityManager.flush();
        entityManager.clear();

        // Then
        MuscleGroup found = muscleGroupRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getName()).isEqualTo("Legs");
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("findAll - should return empty list when no muscle groups exist")
    void findAll_shouldReturnEmptyListWhenNoMuscleGroupsExist() {
        // When
        List<MuscleGroup> muscleGroups = muscleGroupRepository.findAll();

        // Then
        assertThat(muscleGroups).isEmpty();
    }
}