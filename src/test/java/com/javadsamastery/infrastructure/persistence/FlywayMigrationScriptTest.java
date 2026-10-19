package com.javadsamastery.infrastructure.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class FlywayMigrationScriptTest {

    @Test
    @DisplayName("V1__initial_schema.sql exists and enforces all required relational invariants")
    void testFlywayScriptInvariants() throws Exception {
        InputStream is = getClass().getResourceAsStream("/db/migration/V1__initial_schema.sql");
        assertNotNull(is, "V1__initial_schema.sql must exist on classpath in /db/migration");

        String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        assertFalse(sql.isBlank(), "Migration script must not be empty");

        // Verify tables
        assertTrue(sql.contains("CREATE TABLE learners"), "Must create learners table");
        assertTrue(sql.contains("CREATE TABLE learning_sessions"), "Must create learning_sessions table");
        assertTrue(sql.contains("CREATE TABLE attempts"), "Must create attempts table");
        assertTrue(sql.contains("CREATE TABLE evidence_records"), "Must create evidence_records table");
        assertTrue(sql.contains("CREATE TABLE topic_mastery_snapshots"), "Must create topic_mastery_snapshots table");

        // Verify composite candidate key on attempts
        assertTrue(sql.contains("uq_attempts_context UNIQUE (attempt_id, session_id, learner_id, topic_id, question_id)"),
            "Must enforce composite context unique key on attempts");

        // Verify partial unique index for single active issued attempt
        assertTrue(sql.contains("uq_active_issued_attempt_per_session"),
            "Must define partial index for active issued attempt");
        assertTrue(sql.contains("WHERE status = 'ISSUED'"),
            "Partial index must filter on status = 'ISSUED'");

        // Verify composite foreign key on evidence_records referencing attempts
        assertTrue(sql.contains("fk_evidence_attempt_context"),
            "Must define composite foreign key fk_evidence_attempt_context");
        assertTrue(sql.contains("REFERENCES attempts(attempt_id, session_id, learner_id, topic_id, question_id)"),
            "Composite foreign key must reference all 5 attempt context columns");
        assertTrue(sql.contains("ON DELETE CASCADE"),
            "Must support cascade on attempt/session/learner deletion");

        // Verify attempt status check constraint
        assertTrue(sql.contains("chk_attempt_status CHECK (status IN ('ISSUED', 'CONSUMED', 'EXPIRED', 'DISCARDED'))"),
            "Must restrict attempt status vocabulary to approved lifecycle states");

        // Verify evidence result check constraint
        assertTrue(sql.contains("chk_evidence_result CHECK (evaluation_result IN ('PASS', 'FAIL', 'PARTIAL', 'HINT', 'INVALID_RESPONSE'))"),
            "Must restrict evaluation results to valid domain outcomes");

        // Verify deterministic chronological replay index
        assertTrue(sql.contains("idx_evidence_learner_topic_chronological"),
            "Must define chronological replay index");
        assertTrue(sql.contains("created_at ASC, evidence_id ASC"),
            "Replay index must sort by created_at ASC and strictly monotonic evidence_id ASC");
    }

    @Test
    @DisplayName("V2__dedicated_idempotency_fingerprint.sql exists and adds dedicated idempotency column")
    void testV2MigrationScriptInvariants() throws Exception {
        InputStream is = getClass().getResourceAsStream("/db/migration/V2__dedicated_idempotency_fingerprint.sql");
        assertNotNull(is, "V2__dedicated_idempotency_fingerprint.sql must exist on classpath in /db/migration");

        String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        assertFalse(sql.isBlank(), "V2 migration script must not be empty");

        assertTrue(sql.contains("ALTER TABLE evidence_records"), "Must alter evidence_records table");
        assertTrue(sql.contains("ADD COLUMN idempotency_fingerprint VARCHAR(64)"), "Must add idempotency_fingerprint column");
    }
}
