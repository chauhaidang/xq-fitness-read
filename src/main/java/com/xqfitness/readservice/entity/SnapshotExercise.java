package com.xqfitness.readservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "snapshot_exercises")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotExercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "snapshot_workout_day_id", nullable = false)
    private Integer snapshotWorkoutDayId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "muscle_group_id", nullable = false)
    private MuscleGroup muscleGroup;

    @Column(name = "exercise_name", nullable = false, length = 200)
    private String exerciseName;

    @Column(name = "total_reps", nullable = false)
    private Integer totalReps = 0;

    @Column(name = "weight", nullable = false, precision = 10, scale = 2)
    private BigDecimal weight = BigDecimal.ZERO;

    @Column(name = "total_sets", nullable = false)
    private Integer totalSets = 0;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
