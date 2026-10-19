package com.javadsamastery.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.*;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real PostgreSQL integration tests validating Flyway schema migrations,
 * composite foreign keys, partial unique indexes, check constraints,
 * and cascading deletion behavior.
 *
 * Automatically verifies Docker daemon availability via Testcontainers DockerClientFactory.
 * If Docker is unavailable in the execution environment, the test reports this limitation explicitly
 * and skips cleanly via JUnit 5 Assumptions.
 */
class PostgresSchemaIntegrationTest {

    private static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @Test
    @DisplayName("Verify Flyway migration, schema constraints, composite FK, partial index, and cascade on real PostgreSQL")
    void testPostgresSchemaInvariantsOnRealPostgres() throws Exception {
        Assumptions.assumeTrue(isDockerAvailable(),
            "Docker daemon is not available in the local execution environment (Testcontainers requires Docker). " +
            "Skipping live PostgreSQL container integration test.");

        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();

            String jdbcUrl = postgres.getJdbcUrl();
            String username = postgres.getUsername();
            String password = postgres.getPassword();

            // 1. Run Flyway Migration from empty database
            Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .load();

            int migrationsApplied = flyway.migrate().migrationsExecuted;
            assertTrue(migrationsApplied >= 1, "At least 1 Flyway migration must be executed");

            try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
                String learnerId = "test-learner-" + UUID.randomUUID();
                UUID sessionId = UUID.randomUUID();
                UUID attemptId1 = UUID.randomUUID();
                UUID attemptId2 = UUID.randomUUID();

                // 2. Insert Learner
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO learners (learner_id, is_anonymous) VALUES (?, ?)")) {
                    ps.setString(1, learnerId);
                    ps.setBoolean(2, true);
                    assertEquals(1, ps.executeUpdate());
                }

                // 3. Insert Session
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO learning_sessions (session_id, learner_id, topic_id, current_state, expires_at) " +
                        "VALUES (?, ?, ?, ?, NOW() + INTERVAL '1 hour')")) {
                    ps.setObject(1, sessionId);
                    ps.setString(2, learnerId);
                    ps.setString(3, "binary-search");
                    ps.setString(4, "CONCEPT_ASSESSMENT");
                    assertEquals(1, ps.executeUpdate());
                }

                // 4. Insert Attempt 1 with status ISSUED
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO attempts (attempt_id, session_id, learner_id, topic_id, question_id, " +
                        "target_stage, question_type, difficulty, expected_evidence, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ISSUED')")) {
                    ps.setObject(1, attemptId1);
                    ps.setObject(2, sessionId);
                    ps.setString(3, learnerId);
                    ps.setString(4, "binary-search");
                    ps.setString(5, "bs-q1");
                    ps.setString(6, "CONCEPT_ASSESSMENT");
                    ps.setString(7, "CONCEPT");
                    ps.setString(8, "EASY");
                    ps.setString(9, "Invariant expected");
                    assertEquals(1, ps.executeUpdate());
                }

                // 5. Test Partial Unique Index (uq_active_issued_attempt_per_session):
                // Attempting to insert a SECOND attempt with status = 'ISSUED' for the same session must FAIL
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO attempts (attempt_id, session_id, learner_id, topic_id, question_id, " +
                        "target_stage, question_type, difficulty, expected_evidence, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ISSUED')")) {
                    ps.setObject(1, attemptId2);
                    ps.setObject(2, sessionId);
                    ps.setString(3, learnerId);
                    ps.setString(4, "binary-search");
                    ps.setString(5, "bs-q2");
                    ps.setString(6, "CONCEPT_ASSESSMENT");
                    ps.setString(7, "CONCEPT");
                    ps.setString(8, "EASY");
                    ps.setString(9, "Invariant expected");
                    assertThrows(SQLException.class, ps::executeUpdate,
                        "Partial unique index must reject a second active ISSUED attempt for the same session");
                }

                // 6. Test Composite Foreign Key on evidence_records:
                // Inserting an evidence record referencing attemptId1 but with MISMATCHED topic must FAIL
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO evidence_records (attempt_id, session_id, learner_id, topic_id, question_id, " +
                        "question_type, difficulty, hints_used, is_retrieval, evaluation_result) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, 0, FALSE, 'PASS')")) {
                    ps.setObject(1, attemptId1);
                    ps.setObject(2, sessionId);
                    ps.setString(3, learnerId);
                    ps.setString(4, "mismatched-topic-id"); // Wrong topic!
                    ps.setString(5, "bs-q1");
                    ps.setString(6, "CONCEPT");
                    ps.setString(7, "EASY");
                    assertThrows(SQLException.class, ps::executeUpdate,
                        "Composite foreign key must reject evidence with mismatched topic");
                }

                // 7. Insert Valid Evidence Record
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO evidence_records (attempt_id, session_id, learner_id, topic_id, question_id, " +
                        "question_type, difficulty, hints_used, is_retrieval, evaluation_result) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, 0, FALSE, 'PASS')")) {
                    ps.setObject(1, attemptId1);
                    ps.setObject(2, sessionId);
                    ps.setString(3, learnerId);
                    ps.setString(4, "binary-search");
                    ps.setString(5, "bs-q1");
                    ps.setString(6, "CONCEPT");
                    ps.setString(7, "EASY");
                    assertEquals(1, ps.executeUpdate());
                }

                // 8. Test Check Constraint on Attempt Status
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO attempts (attempt_id, session_id, learner_id, topic_id, question_id, " +
                        "target_stage, question_type, difficulty, expected_evidence, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ILLEGAL_STATUS')")) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, sessionId);
                    ps.setString(3, learnerId);
                    ps.setString(4, "binary-search");
                    ps.setString(5, "bs-q3");
                    ps.setString(6, "CONCEPT_ASSESSMENT");
                    ps.setString(7, "CONCEPT");
                    ps.setString(8, "EASY");
                    ps.setString(9, "Evidence");
                    assertThrows(SQLException.class, ps::executeUpdate,
                        "Check constraint must reject unauthorized attempt status");
                }

                // 9. Test Cascading Learner Deletion:
                // Deleting the learner must cascade down to session, attempt, and evidence cleanly without error
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM learners WHERE learner_id = ?")) {
                    ps.setString(1, learnerId);
                    assertEquals(1, ps.executeUpdate(), "Must delete learner row");
                }

                // Verify cascade deleted the attempt and evidence record
                try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM attempts WHERE session_id = ?")) {
                    ps.setObject(1, sessionId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(0, rs.getInt(1), "Attempts must be cascade-deleted");
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM evidence_records WHERE session_id = ?")) {
                    ps.setObject(1, sessionId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(0, rs.getInt(1), "Evidence records must be cascade-deleted");
                    }
                }
            }
        }
    }
}
