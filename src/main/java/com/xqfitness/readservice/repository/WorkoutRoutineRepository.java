package com.xqfitness.readservice.repository;

import com.xqfitness.readservice.entity.WorkoutRoutine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkoutRoutineRepository extends JpaRepository<WorkoutRoutine, Long> {

    List<WorkoutRoutine> findByIsActive(Boolean isActive);

    @Query("SELECT DISTINCT r FROM WorkoutRoutine r LEFT JOIN FETCH r.workoutDays WHERE r.id = :id")
    Optional<WorkoutRoutine> findByIdWithDetails(@Param("id") Long id);
}
