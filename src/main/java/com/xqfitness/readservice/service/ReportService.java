package com.xqfitness.readservice.service;

import com.xqfitness.readservice.dto.MuscleGroupDTO;
import com.xqfitness.readservice.dto.MuscleGroupTotalDTO;
import com.xqfitness.readservice.dto.WeeklyReportDTO;
import com.xqfitness.readservice.entity.WeeklySnapshot;
import com.xqfitness.readservice.repository.MuscleGroupRepository;
import com.xqfitness.readservice.repository.WeeklySnapshotRepository;
import com.xqfitness.readservice.repository.WorkoutRoutineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReportService {

    private final WeeklySnapshotRepository weeklySnapshotRepository;
    private final MuscleGroupRepository muscleGroupRepository;
    private final WorkoutRoutineRepository workoutRoutineRepository;
    private final EntityManager entityManager;

    /**
     * Get weekly report for a routine.
     * Calculates week start (Monday of current week) if not provided.
     * Aggregates sets by muscle group from snapshot if exists, otherwise returns all muscle groups with zero sets.
     */
    public WeeklyReportDTO getWeeklyReport(Integer routineId, LocalDate weekStartDate) {
        log.info("Generating weekly report for routineId: {}, weekStartDate: {}", routineId, weekStartDate);

        // Validate routine exists
        if (!workoutRoutineRepository.existsById(routineId)) {
            log.warn("Routine not found: {}", routineId);
            throw new IllegalArgumentException("Routine not found: " + routineId);
        }

        // Calculate week start if not provided (Monday of current week, ISO 8601)
        // Use UTC to match write-service behavior and ensure timezone consistency
        LocalDate weekStart = weekStartDate != null ? weekStartDate : calculateWeekStart(LocalDate.now(ZoneId.of("UTC")));
        log.debug("Using week start date: {}", weekStart);

        // Query for snapshot
        Optional<WeeklySnapshot> snapshotOpt = weeklySnapshotRepository.findByRoutineIdAndWeekStartDate(routineId, weekStart);

        if (snapshotOpt.isPresent()) {
            WeeklySnapshot snapshot = snapshotOpt.get();
            log.info("Snapshot found for routineId: {}, weekStartDate: {}", routineId, weekStart);

            // Aggregate sets by muscle_group_id using SQL GROUP BY and SUM
            List<MuscleGroupTotalDTO> muscleGroupTotals = aggregateSetsByMuscleGroup(routineId, weekStart);

            return new WeeklyReportDTO(
                    routineId,
                    weekStart,
                    true,
                    snapshot.getCreatedAt(),
                    muscleGroupTotals
            );
        } else {
            log.info("No snapshot found for routineId: {}, weekStartDate: {}. Returning empty report.", routineId, weekStart);

            // Return all muscle groups with zero sets
            List<MuscleGroupTotalDTO> muscleGroupTotals = getAllMuscleGroupsWithZeroSets();

            return new WeeklyReportDTO(
                    routineId,
                    weekStart,
                    false,
                    null,
                    muscleGroupTotals
            );
        }
    }

    /**
     * Calculate Monday of current week (ISO 8601 week start).
     * ISO 8601 defines Monday as the first day of the week.
     * Uses UTC timezone to match write-service behavior and ensure consistency.
     * 
     * @param date The date to calculate week start for (should be in UTC)
     * @return LocalDate representing Monday of the week containing the given date
     */
    public LocalDate calculateWeekStart(LocalDate date) {
        // Date should already be in UTC when passed from getWeeklyReport
        // This method calculates Monday of the week containing the given date
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        int daysToSubtract = dayOfWeek.getValue() - DayOfWeek.MONDAY.getValue();
        if (daysToSubtract < 0) {
            daysToSubtract += 7; // Handle Sunday (value 7)
        }
        return date.minusDays(daysToSubtract);
    }

    /**
     * Aggregate number_of_sets by muscle_group_id from snapshot_workout_day_sets.
     * Uses SQL GROUP BY and SUM for efficient aggregation.
     * Ensures all muscle groups are included, even if they have zero sets in the snapshot.
     */
    private List<MuscleGroupTotalDTO> aggregateSetsByMuscleGroup(Integer routineId, LocalDate weekStartDate) {
        String sql = """
            SELECT 
                mg.id AS muscle_group_id,
                mg.name AS muscle_group_name,
                mg.description AS muscle_group_description,
                mg.created_at AS muscle_group_created_at,
                COALESCE(SUM(swds.number_of_sets), 0) AS total_sets
            FROM muscle_groups mg
            LEFT JOIN snapshot_workout_day_sets swds ON mg.id = swds.muscle_group_id
            LEFT JOIN snapshot_workout_days swd ON swds.snapshot_workout_day_id = swd.id
            LEFT JOIN weekly_snapshots ws ON swd.snapshot_id = ws.id AND ws.routine_id = :routineId AND ws.week_start_date = :weekStartDate
            GROUP BY mg.id, mg.name, mg.description, mg.created_at
            ORDER BY mg.name
            """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("routineId", routineId);
        query.setParameter("weekStartDate", weekStartDate);

        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();

        List<MuscleGroupTotalDTO> totals = new ArrayList<>();
        for (Object[] row : results) {
            Integer muscleGroupId = ((Number) row[0]).intValue();
            String muscleGroupName = (String) row[1];
            String muscleGroupDescription = (String) row[2];
            java.sql.Timestamp createdAt = (java.sql.Timestamp) row[3];
            Long totalSets = ((Number) row[4]).longValue();

            MuscleGroupDTO muscleGroupDTO = new MuscleGroupDTO(
                    muscleGroupId,
                    muscleGroupName,
                    muscleGroupDescription,
                    createdAt != null ? createdAt.toLocalDateTime() : null
            );

            totals.add(new MuscleGroupTotalDTO(muscleGroupDTO, totalSets.intValue()));
        }

        return totals;
    }

    /**
     * Get all muscle groups with zero sets (for empty report when no snapshot exists).
     */
    private List<MuscleGroupTotalDTO> getAllMuscleGroupsWithZeroSets() {
        return muscleGroupRepository.findAll().stream()
                .map(mg -> new MuscleGroupTotalDTO(
                        MuscleGroupDTO.fromEntity(mg),
                        0
                ))
                .sorted((a, b) -> a.getMuscleGroup().getName().compareTo(b.getMuscleGroup().getName()))
                .collect(Collectors.toList());
    }
}
