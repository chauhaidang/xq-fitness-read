package com.xqfitness.readservice.component;

import java.sql.*;
import java.time.LocalDate;

/**
 * Database fixture for component tests. Provides JDBC connection and CRUD helpers
 * so each test can seed data in isolation.
 * <p>
 * Connects to the same database as the read-service (hardcoded to match test-env and scripts/query-db.sh).
 */
public final class DbTestFixture {

    /** Hardcoded connection — matches test-env DB and scripts/query-db.sh (localhost:5432, xq_fitness, xq_user). */
    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/xq_fitness?user=xq_user&password=xq_password";

    private DbTestFixture() {
    }

    public static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(JDBC_URL);
        conn.setAutoCommit(true);
        return conn;
    }

    /** Returns the JDBC URL used for the current connection (for debugging). */
    public static String getConnectionUrl() {
        return JDBC_URL;
    }

    /** Checks if a routine exists by id (for debugging — verify fixture sees its own write). */
    public static boolean routineExists(Connection conn, long routineId) throws SQLException {
        String sql = "SELECT 1 FROM workout_routines WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, routineId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static LocalDate getCurrentWeekStart() {
        LocalDate today = LocalDate.now();
        int daysToSubtract = today.getDayOfWeek().getValue() - java.time.DayOfWeek.MONDAY.getValue();
        if (daysToSubtract < 0) daysToSubtract += 7;
        return today.minusDays(daysToSubtract);
    }

    public static Long createRoutine(Connection conn, String name, String description, boolean isActive) throws SQLException {
        String sql = "INSERT INTO workout_routines (name, description, is_active, created_at, updated_at) " +
                "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.setString(2, description);
            stmt.setBoolean(3, isActive);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong("id") : null;
            }
        }
    }

    public static Long createWorkoutDay(Connection conn, Long routineId, int dayNumber, String dayName, String notes) throws SQLException {
        String sql = "INSERT INTO workout_days (routine_id, day_number, day_name, notes, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, routineId);
            stmt.setInt(2, dayNumber);
            stmt.setString(3, dayName);
            if (notes != null) stmt.setString(4, notes);
            else stmt.setNull(4, Types.VARCHAR);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong("id") : null;
            }
        }
    }

    public static Long createWorkoutDaySet(Connection conn, Long workoutDayId, int muscleGroupId, int numberOfSets, String notes) throws SQLException {
        String sql = "INSERT INTO workout_day_sets (workout_day_id, muscle_group_id, number_of_sets, notes, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, workoutDayId);
            stmt.setInt(2, muscleGroupId);
            stmt.setInt(3, numberOfSets);
            if (notes != null) stmt.setString(4, notes);
            else stmt.setNull(4, Types.VARCHAR);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong("id") : null;
            }
        }
    }

    public static Long createSnapshot(Connection conn, Long routineId, LocalDate weekStart) throws SQLException {
        String sql = "INSERT INTO weekly_snapshots (routine_id, week_start_date, created_at, updated_at) " +
                "VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, routineId);
            stmt.setDate(2, Date.valueOf(weekStart));
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong("id") : null;
            }
        }
    }

    public static Long createSnapshotWorkoutDay(Connection conn, Long snapshotId, Long originalWorkoutDayId, int dayNumber, String dayName, String notes) throws SQLException {
        String sql = "INSERT INTO snapshot_workout_days (snapshot_id, original_workout_day_id, day_number, day_name, notes, created_at) " +
                "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP) RETURNING id";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, snapshotId);
            stmt.setLong(2, originalWorkoutDayId);
            stmt.setInt(3, dayNumber);
            stmt.setString(4, dayName);
            if (notes != null) stmt.setString(5, notes);
            else stmt.setNull(5, Types.VARCHAR);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong("id") : null;
            }
        }
    }

    public static Long createSnapshotWorkoutDaySet(Connection conn, Long snapshotWorkoutDayId, Long originalWorkoutDaySetId, int muscleGroupId, int numberOfSets, String notes) throws SQLException {
        String sql = "INSERT INTO snapshot_workout_day_sets (snapshot_workout_day_id, original_workout_day_set_id, muscle_group_id, number_of_sets, notes, created_at) " +
                "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP) RETURNING id";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, snapshotWorkoutDayId);
            stmt.setLong(2, originalWorkoutDaySetId);
            stmt.setInt(3, muscleGroupId);
            stmt.setInt(4, numberOfSets);
            if (notes != null) stmt.setString(5, notes);
            else stmt.setNull(5, Types.VARCHAR);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong("id") : null;
            }
        }
    }

    /**
     * Deletes a routine and all dependent rows (cascade order to satisfy FKs).
     * Caller must use the same connection/transaction as for the created data.
     */
    public static void deleteRoutine(Connection conn, Long routineId) throws SQLException {
        if (routineId == null) return;
        // Cascade delete: snapshot_exercises -> snapshot_workout_day_sets -> snapshot_workout_days -> weekly_snapshots -> workout_day_sets -> workout_days -> workout_routines
        String deleteSnapshotExercises = "DELETE FROM snapshot_exercises WHERE snapshot_workout_day_id IN " +
                "(SELECT id FROM snapshot_workout_days WHERE snapshot_id IN (SELECT id FROM weekly_snapshots WHERE routine_id = ?))";
        String deleteSnapshotSets = "DELETE FROM snapshot_workout_day_sets WHERE snapshot_workout_day_id IN " +
                "(SELECT id FROM snapshot_workout_days WHERE snapshot_id IN (SELECT id FROM weekly_snapshots WHERE routine_id = ?))";
        String deleteSnapshotDays = "DELETE FROM snapshot_workout_days WHERE snapshot_id IN (SELECT id FROM weekly_snapshots WHERE routine_id = ?)";
        String deleteSnapshots = "DELETE FROM weekly_snapshots WHERE routine_id = ?";
        String deleteDaySets = "DELETE FROM workout_day_sets WHERE workout_day_id IN (SELECT id FROM workout_days WHERE routine_id = ?)";
        String deleteDays = "DELETE FROM workout_days WHERE routine_id = ?";
        String deleteRoutineSql = "DELETE FROM workout_routines WHERE id = ?";
        try (PreparedStatement s0 = conn.prepareStatement(deleteSnapshotExercises);
             PreparedStatement s1 = conn.prepareStatement(deleteSnapshotSets);
             PreparedStatement s2 = conn.prepareStatement(deleteSnapshotDays);
             PreparedStatement s3 = conn.prepareStatement(deleteSnapshots);
             PreparedStatement s4 = conn.prepareStatement(deleteDaySets);
             PreparedStatement s5 = conn.prepareStatement(deleteDays);
             PreparedStatement s6 = conn.prepareStatement(deleteRoutineSql)) {
            s0.setLong(1, routineId);
            s0.executeUpdate();
            s1.setLong(1, routineId);
            s1.executeUpdate();
            s2.setLong(1, routineId);
            s2.executeUpdate();
            s3.setLong(1, routineId);
            s3.executeUpdate();
            s4.setLong(1, routineId);
            s4.executeUpdate();
            s5.setLong(1, routineId);
            s5.executeUpdate();
            s6.setLong(1, routineId);
            s6.executeUpdate();
        }
    }
}
