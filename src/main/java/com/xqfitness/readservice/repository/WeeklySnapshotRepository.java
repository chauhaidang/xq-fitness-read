package com.xqfitness.readservice.repository;

import com.xqfitness.readservice.entity.WeeklySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface WeeklySnapshotRepository extends JpaRepository<WeeklySnapshot, Integer> {

    Optional<WeeklySnapshot> findByRoutineIdAndWeekStartDate(Integer routineId, LocalDate weekStartDate);
}
