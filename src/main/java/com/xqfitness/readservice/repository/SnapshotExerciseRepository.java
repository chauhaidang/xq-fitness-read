package com.xqfitness.readservice.repository;

import com.xqfitness.readservice.entity.SnapshotExercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SnapshotExerciseRepository extends JpaRepository<SnapshotExercise, Integer> {

    List<SnapshotExercise> findBySnapshotWorkoutDayIdInOrderById(List<Integer> snapshotWorkoutDayIds);
}
