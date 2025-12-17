package com.xqfitness.readservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "snapshot_workout_day_sets")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotWorkoutDaySet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_workout_day_id", nullable = false)
    private SnapshotWorkoutDay snapshotWorkoutDay;

    @Column(name = "original_workout_day_set_id", nullable = false)
    private Integer originalWorkoutDaySetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "muscle_group_id", nullable = false)
    private MuscleGroup muscleGroup;

    @Column(name = "number_of_sets", nullable = false)
    private Integer numberOfSets;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
