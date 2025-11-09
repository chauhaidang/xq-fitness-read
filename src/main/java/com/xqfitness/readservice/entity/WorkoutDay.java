package com.xqfitness.readservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "workout_days", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"routine_id", "day_number"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkoutDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routine_id", nullable = false)
    private WorkoutRoutine routine;

    @Column(name = "day_number", nullable = false)
    private Integer dayNumber;

    @Column(name = "day_name", nullable = false, length = 100)
    private String dayName;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "workoutDay", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<WorkoutDaySet> sets = new ArrayList<>();

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
