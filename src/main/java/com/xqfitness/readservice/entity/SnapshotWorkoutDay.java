package com.xqfitness.readservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "snapshot_workout_days")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotWorkoutDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private WeeklySnapshot snapshot;

    @Column(name = "original_workout_day_id", nullable = false)
    private Integer originalWorkoutDayId;

    @Column(name = "day_number", nullable = false)
    private Integer dayNumber;

    @Column(name = "day_name", nullable = false, length = 100)
    private String dayName;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "snapshotWorkoutDay", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SnapshotWorkoutDaySet> sets = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
