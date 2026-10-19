package com.javadsamastery.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javadsamastery.domain.evidence.AttemptContext;
import com.javadsamastery.domain.evidence.EvaluationResult;
import com.javadsamastery.domain.evidence.EvidenceRecord;
import com.javadsamastery.domain.lesson.LearningState;
import com.javadsamastery.domain.lesson.QuestionType;
import com.javadsamastery.domain.ports.AttemptRepositoryPort.AttemptRecord;
import com.javadsamastery.domain.ports.EvidenceRepositoryPort.PersistedEvidence;
import com.javadsamastery.domain.ports.SessionRepositoryPort.SessionSnapshot;
import com.javadsamastery.domain.ports.TopicMasteryRepositoryPort.MasterySnapshotRecord;
import com.javadsamastery.domain.problem.ProblemDifficulty;
import com.javadsamastery.infrastructure.persistence.entity.*;
import com.javadsamastery.infrastructure.persistence.mapper.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;

import java.net.Socket;
import java.sql.*;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RealPostgresVerificationTest {

    private static final String JDBC_URL = System.getProperty("spring.datasource.url",
            System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://127.0.0.1:5433/java_dsa_mastery_phase2b_test"));
    private static final String USERNAME = System.getProperty("spring.datasource.username",
            System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "postgres"));
    private static final String PASSWORD = System.getProperty("spring.datasource.password",
            System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));

    private static boolean isLocalPostgresAvailable() {
        try {
            java.net.URI uri = java.net.URI.create(JDBC_URL.substring(5));
            String host = uri.getHost() != null ? uri.getHost() : "127.0.0.1";
            int port = uri.getPort() > 0 ? uri.getPort() : 5433;
            try (Socket socket = new Socket(host, port)) {
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeAll
    static void checkEnvironment() {
        Assumptions.assumeTrue(isLocalPostgresAvailable(),
            "Local test PostgreSQL 18 instance on port 5433 is not running; skipping RealPostgresVerificationTest.");
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD);
    }

    @Test
    @Order(1)
    @DisplayName("Task 4: Run Flyway migration against clean java_dsa_mastery_phase2b_test database")
    void testFlywayMigration_ExecutesSuccessfully() {
        Flyway flyway = Flyway.configure()
            .dataSource(JDBC_URL, USERNAME, PASSWORD)
            .locations("classpath:db/migration")
            .load();

        flyway.migrate();
        assertNotNull(flyway.info().current(), "Flyway current schema version must not be null");
        assertEquals("2", flyway.info().current().getVersion().getVersion(), "Flyway current schema version must be 2");

        // Verify flyway_schema_history table
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            try (ResultSet rs1 = stmt.executeQuery("SELECT success FROM flyway_schema_history WHERE version = '1'")) {
                assertTrue(rs1.next(), "V1 entry must exist in flyway_schema_history");
                assertTrue(rs1.getBoolean("success"), "Migration V1 must be recorded as successful");
            }
            try (ResultSet rs2 = stmt.executeQuery("SELECT success FROM flyway_schema_history WHERE version = '2'")) {
                assertTrue(rs2.next(), "V2 entry must exist in flyway_schema_history");
                assertTrue(rs2.getBoolean("success"), "Migration V2 must be recorded as successful");
            }
        } catch (SQLException e) {
            fail("Failed to verify flyway_schema_history: " + e.getMessage());
        }
    }

    @Test
    @Order(2)
    @DisplayName("Task 5A: Composite foreign key rejects mismatched context (learner, topic, question, session)")
    void testCompositeForeignKey_RejectsMismatchedContext() throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                String learnerId = "test-learner-fk-" + UUID.randomUUID();
                UUID sessionId = UUID.randomUUID();
                UUID attemptId = UUID.randomUUID();

                // Insert Learner
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO learners (learner_id, is_anonymous) VALUES (?, ?)")) {
                    ps.setString(1, learnerId);
                    ps.setBoolean(2, true);
                    ps.executeUpdate();
                }

                // Insert Session
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO learning_sessions (session_id, learner_id, topic_id, current_state, expires_at) " +
                        "VALUES (?, ?, 'binary-search', 'CONCEPT_ASSESSMENT', NOW() + INTERVAL '1 hour')")) {
                    ps.setObject(1, sessionId);
                    ps.setString(2, learnerId);
                    ps.executeUpdate();
                }

                // Insert Attempt
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO attempts (attempt_id, session_id, learner_id, topic_id, question_id, " +
                        "target_stage, question_type, difficulty, expected_evidence, status) " +
                        "VALUES (?, ?, ?, 'binary-search', 'bs-q1', 'CONCEPT_ASSESSMENT', 'CONCEPT', 'EASY', 'expected', 'ISSUED')")) {
                    ps.setObject(1, attemptId);
                    ps.setObject(2, sessionId);
                    ps.setString(3, learnerId);
                    ps.executeUpdate();
                }

                // Case 1: Mismatched topic
                Savepoint spTopic = conn.setSavepoint();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO evidence_records (attempt_id, session_id, learner_id, topic_id, question_id, " +
                        "question_type, difficulty, hints_used, is_retrieval, evaluation_result) " +
                        "VALUES (?, ?, ?, 'mismatched-topic', 'bs-q1', 'CONCEPT', 'EASY', 0, FALSE, 'PASS')")) {
                    ps.setObject(1, attemptId);
                    ps.setObject(2, sessionId);
                    ps.setString(3, learnerId);
                    assertThrows(SQLException.class, ps::executeUpdate,
                        "Must reject evidence record with mismatched topic_id");
                }
                conn.rollback(spTopic);

                // Case 2: Mismatched question
                Savepoint spQuestion = conn.setSavepoint();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO evidence_records (attempt_id, session_id, learner_id, topic_id, question_id, " +
                        "question_type, difficulty, hints_used, is_retrieval, evaluation_result) " +
                        "VALUES (?, ?, ?, 'binary-search', 'wrong-question-id', 'CONCEPT', 'EASY', 0, FALSE, 'PASS')")) {
                    ps.setObject(1, attemptId);
                    ps.setObject(2, sessionId);
                    ps.setString(3, learnerId);
                    assertThrows(SQLException.class, ps::executeUpdate,
                        "Must reject evidence record with mismatched question_id");
                }
                conn.rollback(spQuestion);

                // Case 3: Matching context succeeds
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO evidence_records (attempt_id, session_id, learner_id, topic_id, question_id, " +
                        "question_type, difficulty, hints_used, is_retrieval, evaluation_result) " +
                        "VALUES (?, ?, ?, 'binary-search', 'bs-q1', 'CONCEPT', 'EASY', 0, FALSE, 'PASS')")) {
                    ps.setObject(1, attemptId);
                    ps.setObject(2, sessionId);
                    ps.setString(3, learnerId);
                    assertEquals(1, ps.executeUpdate(), "Valid matching context evidence record must succeed");
                }

            } finally {
                conn.rollback();
            }
        }
    }

    @Test
    @Order(3)
    @DisplayName("Task 5B: Partial unique index rejects a second active issued attempt for a session")
    void testPartialUniqueIndex_RejectsSecondActiveIssuedAttempt() throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                String learnerId = "test-learner-partial-" + UUID.randomUUID();
                UUID sessionId = UUID.randomUUID();
                UUID attempt1 = UUID.randomUUID();
                UUID attempt2 = UUID.randomUUID();

                // Insert Learner
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO learners (learner_id) VALUES (?)")) {
                    ps.setString(1, learnerId);
                    ps.executeUpdate();
                }

                // Insert Session
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO learning_sessions (session_id, learner_id, topic_id, current_state, expires_at) " +
                        "VALUES (?, ?, 'two-pointers', 'DERIVE', NOW() + INTERVAL '1 hour')")) {
                    ps.setObject(1, sessionId);
                    ps.setString(2, learnerId);
                    ps.executeUpdate();
                }

                // Insert Attempt 1 with status ISSUED
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO attempts (attempt_id, session_id, learner_id, topic_id, question_id, " +
                        "target_stage, question_type, difficulty, expected_evidence, status) " +
                        "VALUES (?, ?, ?, 'two-pointers', 'tp-q1', 'DERIVE', 'DERIVATION', 'MEDIUM', 'exp', 'ISSUED')")) {
                    ps.setObject(1, attempt1);
                    ps.setObject(2, sessionId);
                    ps.setString(3, learnerId);
                    assertEquals(1, ps.executeUpdate());
                }

                // Attempting to insert Attempt 2 with status ISSUED for same session must FAIL on partial index
                Savepoint spIndex = conn.setSavepoint();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO attempts (attempt_id, session_id, learner_id, topic_id, question_id, " +
                        "target_stage, question_type, difficulty, expected_evidence, status) " +
                        "VALUES (?, ?, ?, 'two-pointers', 'tp-q2', 'DERIVE', 'DERIVATION', 'MEDIUM', 'exp', 'ISSUED')")) {
                    ps.setObject(1, attempt2);
                    ps.setObject(2, sessionId);
                    ps.setString(3, learnerId);
                    assertThrows(SQLException.class, ps::executeUpdate,
                        "Partial unique index uq_active_issued_attempt_per_session must reject second active attempt");
                }
                conn.rollback(spIndex);

                // Transition Attempt 1 to CONSUMED
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE attempts SET status = 'CONSUMED', consumed_at = NOW() WHERE attempt_id = ?")) {
                    ps.setObject(1, attempt1);
                    assertEquals(1, ps.executeUpdate());
                }

                // Now Attempt 2 with status ISSUED must succeed
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO attempts (attempt_id, session_id, learner_id, topic_id, question_id, " +
                        "target_stage, question_type, difficulty, expected_evidence, status) " +
                        "VALUES (?, ?, ?, 'two-pointers', 'tp-q2', 'DERIVE', 'DERIVATION', 'MEDIUM', 'exp', 'ISSUED')")) {
                    ps.setObject(1, attempt2);
                    ps.setObject(2, sessionId);
                    ps.setString(3, learnerId);
                    assertEquals(1, ps.executeUpdate(),
                        "Inserting ISSUED attempt must succeed once previous attempt is CONSUMED");
                }

            } finally {
                conn.rollback();
            }
        }
    }

    @Test
    @Order(4)
    @DisplayName("Task 5C: Attempt status check constraints reject invalid values")
    void testAttemptStatusCheckConstraint() throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                String learnerId = "test-learner-chk-" + UUID.randomUUID();
                UUID sessionId = UUID.randomUUID();

                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO learners (learner_id) VALUES (?)")) {
                    ps.setString(1, learnerId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO learning_sessions (session_id, learner_id, topic_id, current_state, expires_at) " +
                        "VALUES (?, ?, 'hashing', 'CONTENT_INTRO', NOW() + INTERVAL '1 hour')")) {
                    ps.setObject(1, sessionId);
                    ps.setString(2, learnerId);
                    ps.executeUpdate();
                }

                // Invalid status 'VENDED' (replaced by ISSUED) or 'BOGUS'
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO attempts (attempt_id, session_id, learner_id, topic_id, question_id, " +
                        "target_stage, question_type, difficulty, expected_evidence, status) " +
                        "VALUES (?, ?, ?, 'hashing', 'h-1', 'CONTENT_INTRO', 'CONCEPT', 'EASY', 'e', 'BOGUS_STATUS')")) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, sessionId);
                    ps.setString(3, learnerId);
                    assertThrows(SQLException.class, ps::executeUpdate,
                        "chk_attempt_status must reject unauthorized status values");
                }

            } finally {
                conn.rollback();
            }
        }
    }

    @Test
    @Order(5)
    @DisplayName("Task 5D: Learner deletion follows designed cascading policy without foreign key restriction errors")
    void testLearnerDeletion_CascadesCleanly() throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                String learnerId = "test-learner-cascade-" + UUID.randomUUID();
                UUID sessionId = UUID.randomUUID();
                UUID attemptId = UUID.randomUUID();

                // 1. Insert Learner
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO learners (learner_id) VALUES (?)")) {
                    ps.setString(1, learnerId);
                    ps.executeUpdate();
                }

                // 2. Insert Session
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO learning_sessions (session_id, learner_id, topic_id, current_state, expires_at) " +
                        "VALUES (?, ?, 'sliding-window', 'IMPLEMENTATION', NOW() + INTERVAL '1 hour')")) {
                    ps.setObject(1, sessionId);
                    ps.setString(2, learnerId);
                    ps.executeUpdate();
                }

                // 3. Insert Attempt
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO attempts (attempt_id, session_id, learner_id, topic_id, question_id, " +
                        "target_stage, question_type, difficulty, expected_evidence, status) " +
                        "VALUES (?, ?, ?, 'sliding-window', 'sw-01', 'IMPLEMENTATION', 'IMPLEMENTATION', 'HARD', 'e', 'CONSUMED')")) {
                    ps.setObject(1, attemptId);
                    ps.setObject(2, sessionId);
                    ps.setString(3, learnerId);
                    ps.executeUpdate();
                }

                // 4. Insert Evidence
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO evidence_records (attempt_id, session_id, learner_id, topic_id, question_id, " +
                        "question_type, difficulty, hints_used, is_retrieval, evaluation_result) " +
                        "VALUES (?, ?, ?, 'sliding-window', 'sw-01', 'IMPLEMENTATION', 'HARD', 0, FALSE, 'PASS')")) {
                    ps.setObject(1, attemptId);
                    ps.setObject(2, sessionId);
                    ps.setString(3, learnerId);
                    ps.executeUpdate();
                }

                // 5. Insert Snapshot
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO topic_mastery_snapshots (learner_id, topic_id, mastery_level) " +
                        "VALUES (?, 'sliding-window', 'NOVICE')")) {
                    ps.setString(1, learnerId);
                    ps.executeUpdate();
                }

                // 6. Delete Learner (MUST cascade to all child tables cleanly)
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM learners WHERE learner_id = ?")) {
                    ps.setString(1, learnerId);
                    assertEquals(1, ps.executeUpdate(), "Must delete learner row");
                }

                // 7. Verify all child records were cascade-deleted
                try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM learning_sessions WHERE session_id = ?")) {
                    ps.setObject(1, sessionId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(0, rs.getInt(1), "Session must be cascade deleted");
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM attempts WHERE session_id = ?")) {
                    ps.setObject(1, sessionId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(0, rs.getInt(1), "Attempts must be cascade deleted");
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM evidence_records WHERE session_id = ?")) {
                    ps.setObject(1, sessionId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(0, rs.getInt(1), "Evidence records must be cascade deleted");
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM topic_mastery_snapshots WHERE learner_id = ?")) {
                    ps.setString(1, learnerId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(0, rs.getInt(1), "Snapshots must be cascade deleted");
                    }
                }

            } finally {
                conn.rollback();
            }
        }
    }

    @Test
    @Order(6)
    @DisplayName("Task 5E: Topic mastery snapshot creation handles initially missing row")
    void testTopicMasterySnapshot_HandlesInitiallyMissingRow() throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                String learnerId = "test-learner-snap-" + UUID.randomUUID();
                String topicId = "prefix-sum";

                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO learners (learner_id) VALUES (?)")) {
                    ps.setString(1, learnerId);
                    ps.executeUpdate();
                }

                // Query snapshot: should be empty
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM topic_mastery_snapshots WHERE learner_id = ? AND topic_id = ?")) {
                    ps.setString(1, learnerId);
                    ps.setString(2, topicId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(0, rs.getInt(1), "Snapshot must not exist initially");
                    }
                }

                // Insert snapshot
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO topic_mastery_snapshots (learner_id, topic_id, mastery_level, independent_concept_passes, version) " +
                        "VALUES (?, ?, 'FAMILIAR', 2, 0)")) {
                    ps.setString(1, learnerId);
                    ps.setString(2, topicId);
                    assertEquals(1, ps.executeUpdate());
                }

                // Duplicate insert on composite PK must be rejected
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO topic_mastery_snapshots (learner_id, topic_id, mastery_level) " +
                        "VALUES (?, ?, 'NOVICE')")) {
                    ps.setString(1, learnerId);
                    ps.setString(2, topicId);
                    assertThrows(SQLException.class, ps::executeUpdate,
                        "Composite PK (learner_id, topic_id) must prevent duplicate snapshots");
                }

            } finally {
                conn.rollback();
            }
        }
    }

    @Test
    @Order(7)
    @DisplayName("Task 5F: Repository mappers preserve all intended values in real PostgreSQL")
    void testRepositoryMappings_PreserveIntendedValues() throws SQLException {
        ObjectMapper objectMapper = new ObjectMapper();
        AttemptEntityMapper attemptMapper = new AttemptEntityMapper(objectMapper);
        EvidenceEntityMapper evidenceMapper = new EvidenceEntityMapper();
        TopicMasterySnapshotMapper snapshotMapper = new TopicMasterySnapshotMapper(objectMapper);
        SessionEntityMapper sessionMapper = new SessionEntityMapper();

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                String learnerId = "test-learner-mapper-" + UUID.randomUUID();
                UUID sessionId = UUID.randomUUID();
                UUID attemptId = UUID.randomUUID();

                // 1. Insert Learner
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO learners (learner_id) VALUES (?)")) {
                    ps.setString(1, learnerId);
                    ps.executeUpdate();
                }

                // 2. Session mapping test
                SessionSnapshot sessionSnapshot = new SessionSnapshot(
                    sessionId, learnerId, "linear-search", "IMPLEMENTATION",
                    3, 0, 1, false, 0L, Instant.now(), Instant.now(), Instant.now().plusSeconds(3600)
                );
                SessionEntity sessionEntity = sessionMapper.toEntity(sessionSnapshot);

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO learning_sessions (session_id, learner_id, topic_id, current_state, " +
                        "nominal_stage_index, pedagogical_failures, total_attempts_in_state, is_completed, version, expires_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setObject(1, sessionEntity.getSessionId());
                    ps.setString(2, sessionEntity.getLearnerId());
                    ps.setString(3, sessionEntity.getTopicId());
                    ps.setString(4, sessionEntity.getCurrentState());
                    ps.setInt(5, sessionEntity.getNominalStageIndex());
                    ps.setInt(6, sessionEntity.getPedagogicalFailures());
                    ps.setInt(7, sessionEntity.getTotalAttemptsInState());
                    ps.setBoolean(8, sessionEntity.isCompleted());
                    ps.setLong(9, sessionEntity.getVersion());
                    ps.setTimestamp(10, Timestamp.from(sessionEntity.getExpiresAt()));
                    assertEquals(1, ps.executeUpdate());
                }

                // 3. Attempt mapping test with JSON list
                AttemptContext attemptCtx = new AttemptContext(
                    attemptId.toString(), "linear-search", "ls-01",
                    LearningState.IMPLEMENTATION, QuestionType.IMPLEMENTATION,
                    ProblemDifficulty.MEDIUM, learnerId, List.of("EARLY_RETURN_BUG", "OFF_BY_ONE")
                );
                AttemptRecord attemptRecord = new AttemptRecord(
                    attemptCtx, sessionId, "loop invariant", 1, "ISSUED", Instant.now(), null
                );
                AttemptEntity attemptEntity = attemptMapper.toEntity(attemptRecord);

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO attempts (attempt_id, session_id, learner_id, topic_id, question_id, " +
                        "target_stage, question_type, difficulty, expected_evidence, allowed_misconceptions, hints_requested, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)")) {
                    ps.setObject(1, attemptEntity.getAttemptId());
                    ps.setObject(2, attemptEntity.getSessionId());
                    ps.setString(3, attemptEntity.getLearnerId());
                    ps.setString(4, attemptEntity.getTopicId());
                    ps.setString(5, attemptEntity.getQuestionId());
                    ps.setString(6, attemptEntity.getTargetStage());
                    ps.setString(7, attemptEntity.getQuestionType());
                    ps.setString(8, attemptEntity.getDifficulty());
                    ps.setString(9, attemptEntity.getExpectedEvidence());
                    ps.setString(10, attemptEntity.getAllowedMisconceptions());
                    ps.setInt(11, attemptEntity.getHintsRequested());
                    ps.setString(12, attemptEntity.getStatus());
                    assertEquals(1, ps.executeUpdate());
                }

                // Read attempt back and map to domain
                try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM attempts WHERE attempt_id = ?")) {
                    ps.setObject(1, attemptId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        AttemptEntity retrieved = new AttemptEntity(
                            (UUID) rs.getObject("attempt_id"),
                            (UUID) rs.getObject("session_id"),
                            rs.getString("learner_id"),
                            rs.getString("topic_id"),
                            rs.getString("question_id"),
                            rs.getString("target_stage"),
                            rs.getString("question_type"),
                            rs.getString("difficulty"),
                            rs.getString("expected_evidence"),
                            rs.getString("allowed_misconceptions"),
                            rs.getInt("hints_requested"),
                            rs.getString("status"),
                            rs.getTimestamp("created_at").toInstant(),
                            null
                        );
                        AttemptRecord mappedBack = attemptMapper.toDomain(retrieved);
                        assertEquals("linear-search", mappedBack.context().topicId());
                        assertEquals(2, mappedBack.context().allowedMisconceptions().size());
                        assertTrue(mappedBack.context().allowedMisconceptions().contains("EARLY_RETURN_BUG"));
                    }
                }

                // 4. Evidence mapping test
                EvidenceRecord evidence = new EvidenceRecord(
                    learnerId, "linear-search", "ls-01",
                    ProblemDifficulty.MEDIUM, QuestionType.IMPLEMENTATION,
                    1, false, EvaluationResult.PASS, null, Instant.now()
                );
                EvidenceEntity evidenceEntity = evidenceMapper.toEntity(evidence, attemptId, sessionId, "Well done");

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO evidence_records (attempt_id, session_id, learner_id, topic_id, question_id, " +
                        "question_type, difficulty, hints_used, is_retrieval, evaluation_result, feedback) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setObject(1, evidenceEntity.getAttemptId());
                    ps.setObject(2, evidenceEntity.getSessionId());
                    ps.setString(3, evidenceEntity.getLearnerId());
                    ps.setString(4, evidenceEntity.getTopicId());
                    ps.setString(5, evidenceEntity.getQuestionId());
                    ps.setString(6, evidenceEntity.getQuestionType());
                    ps.setString(7, evidenceEntity.getDifficulty());
                    ps.setInt(8, evidenceEntity.getHintsUsed());
                    ps.setBoolean(9, evidenceEntity.isRetrieval());
                    ps.setString(10, evidenceEntity.getEvaluationResult());
                    ps.setString(11, evidenceEntity.getFeedback());
                    assertEquals(1, ps.executeUpdate());
                }

                // Read evidence back and map to domain
                try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM evidence_records WHERE attempt_id = ?")) {
                    ps.setObject(1, attemptId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        EvidenceEntity retrieved = new EvidenceEntity(
                            (UUID) rs.getObject("attempt_id"),
                            (UUID) rs.getObject("session_id"),
                            rs.getString("learner_id"),
                            rs.getString("topic_id"),
                            rs.getString("question_id"),
                            rs.getString("question_type"),
                            rs.getString("difficulty"),
                            rs.getInt("hints_used"),
                            rs.getBoolean("is_retrieval"),
                            rs.getString("evaluation_result"),
                            rs.getString("misconception_category"),
                            rs.getString("feedback"),
                            rs.getTimestamp("created_at").toInstant()
                        );
                        retrieved.setEvidenceId(rs.getLong("evidence_id"));
                        PersistedEvidence persisted = evidenceMapper.toPersistedDomain(retrieved);
                        assertEquals("ls-01", persisted.evidenceRecord().problemId());
                        assertEquals(EvaluationResult.PASS, persisted.evidenceRecord().result());
                        assertEquals("Well done", persisted.feedback());
                    }
                }

                // 5. Snapshot mapping test with JSON set
                MasterySnapshotRecord snapRecord = new MasterySnapshotRecord(
                    learnerId, "linear-search", "PROFICIENT", 2, 2, 1, 1, 0,
                    Set.of("BOUNDARY_ERROR"), Instant.now(), 1L
                );
                TopicMasterySnapshotEntity snapEntity = snapshotMapper.toEntity(snapRecord);

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO topic_mastery_snapshots (learner_id, topic_id, mastery_level, " +
                        "independent_concept_passes, independent_implementation_passes, transfer_passes, " +
                        "retrieval_passes, consecutive_failures, active_misconceptions, version) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)")) {
                    ps.setString(1, snapEntity.getLearnerId());
                    ps.setString(2, snapEntity.getTopicId());
                    ps.setString(3, snapEntity.getMasteryLevel());
                    ps.setInt(4, snapEntity.getIndependentConceptPasses());
                    ps.setInt(5, snapEntity.getIndependentImplementationPasses());
                    ps.setInt(6, snapEntity.getTransferPasses());
                    ps.setInt(7, snapEntity.getRetrievalPasses());
                    ps.setInt(8, snapEntity.getConsecutiveFailures());
                    ps.setString(9, snapEntity.getActiveMisconceptions());
                    ps.setLong(10, snapEntity.getVersion());
                    assertEquals(1, ps.executeUpdate());
                }

                // Read snapshot back and map to domain
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT * FROM topic_mastery_snapshots WHERE learner_id = ? AND topic_id = 'linear-search'")) {
                    ps.setString(1, learnerId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        TopicMasterySnapshotEntity retrieved = new TopicMasterySnapshotEntity(
                            rs.getString("learner_id"),
                            rs.getString("topic_id"),
                            rs.getString("mastery_level"),
                            rs.getInt("independent_concept_passes"),
                            rs.getInt("independent_implementation_passes"),
                            rs.getInt("transfer_passes"),
                            rs.getInt("retrieval_passes"),
                            rs.getInt("consecutive_failures"),
                            rs.getString("active_misconceptions"),
                            rs.getTimestamp("updated_at").toInstant(),
                            rs.getLong("version")
                        );
                        MasterySnapshotRecord mappedBack = snapshotMapper.toDomain(retrieved);
                        assertEquals("PROFICIENT", mappedBack.masteryLevel());
                        assertEquals(1, mappedBack.activeMisconceptions().size());
                        assertTrue(mappedBack.activeMisconceptions().contains("BOUNDARY_ERROR"));
                    }
                }

            } finally {
                conn.rollback();
            }
        }
    }
}
