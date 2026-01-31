package com.xqfitness.readservice.repository;

import com.xqfitness.readservice.entity.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExerciseRepository extends JpaRepository<Exercise, Integer> {

    List<Exercise> findByWorkoutDayIdOrderById(Integer workoutDayId);

    List<Exercise> findByWorkoutDayIdAndMuscleGroupIdOrderById(Integer workoutDayId, Integer muscleGroupId);
}
