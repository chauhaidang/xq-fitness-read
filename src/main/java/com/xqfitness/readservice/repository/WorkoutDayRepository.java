package com.xqfitness.readservice.repository;

import com.xqfitness.readservice.entity.WorkoutDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkoutDayRepository extends JpaRepository<WorkoutDay, Long> {

    @Query("SELECT d FROM WorkoutDay d LEFT JOIN FETCH d.sets s LEFT JOIN FETCH s.muscleGroup WHERE d.routine.id = :routineId ORDER BY d.dayNumber")
    List<WorkoutDay> findByRoutineIdWithSets(@Param("routineId") Long routineId);
}
