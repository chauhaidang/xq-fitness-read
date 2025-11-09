package com.xqfitness.readservice.repository;

import com.xqfitness.readservice.entity.MuscleGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MuscleGroupRepository extends JpaRepository<MuscleGroup, Long> {
}
