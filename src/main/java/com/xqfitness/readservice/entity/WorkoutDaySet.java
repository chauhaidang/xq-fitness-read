package com.xqfitness.readservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "workout_day_sets", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"workout_day_id", "muscle_group_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkoutDaySet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_day_id", nullable = false)
    private WorkoutDay workoutDay;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "muscle_group_id", nullable = false)
    private MuscleGroup muscleGroup;

    @Column(name = "number_of_sets", nullable = false)
    private Integer numberOfSets;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
