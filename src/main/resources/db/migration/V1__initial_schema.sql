-- =============================================================================
-- V1__initial_schema.sql
-- Java DSA Mastery: Hardened PostgreSQL Schema (Phase 2B)
-- =============================================================================

-- 1. Learners (Anonymous or registered learner identity)
CREATE TABLE learners (
    learner_id VARCHAR(64) PRIMARY KEY,
    is_anonymous BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_active_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. Learning Sessions (Interactive lesson journeys per topic)
CREATE TABLE learning_sessions (
    session_id UUID PRIMARY KEY,
    learner_id VARCHAR(64) NOT NULL REFERENCES learners(learner_id) ON DELETE CASCADE,
    topic_id VARCHAR(64) NOT NULL,
    current_state VARCHAR(32) NOT NULL,
    nominal_stage_index INT NOT NULL DEFAULT 0,
    pedagogical_failures INT NOT NULL DEFAULT 0,
    total_attempts_in_state INT NOT NULL DEFAULT 0,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_sessions_learner_topic ON learning_sessions(learner_id, topic_id);
CREATE INDEX idx_sessions_expires_at ON learning_sessions(expires_at) WHERE is_completed = FALSE;

-- 3. Attempts (Single-use question attempt tokens)
CREATE TABLE attempts (
    attempt_id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES learning_sessions(session_id) ON DELETE CASCADE,
    learner_id VARCHAR(64) NOT NULL REFERENCES learners(learner_id) ON DELETE CASCADE,
    topic_id VARCHAR(64) NOT NULL,
    question_id VARCHAR(64) NOT NULL,
    target_stage VARCHAR(32) NOT NULL,
    question_type VARCHAR(32) NOT NULL,
    difficulty VARCHAR(16) NOT NULL,
    expected_evidence TEXT NOT NULL,
    allowed_misconceptions JSONB NOT NULL DEFAULT '[]'::jsonb,
    hints_requested INT NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL DEFAULT 'ISSUED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    consumed_at TIMESTAMPTZ NULL,

    -- Composite candidate key ensuring all context dimensions match exactly:
    CONSTRAINT uq_attempts_context UNIQUE (attempt_id, session_id, learner_id, topic_id, question_id),

    -- Status validation check:
    CONSTRAINT chk_attempt_status CHECK (status IN ('ISSUED', 'CONSUMED', 'EXPIRED', 'DISCARDED'))
);

CREATE INDEX idx_attempts_session_id ON attempts(session_id);

-- Enforces at most ONE active ISSUED attempt per session at any time:
CREATE UNIQUE INDEX uq_active_issued_attempt_per_session
    ON attempts(session_id)
    WHERE status = 'ISSUED';

-- 4. Evidence Records (Authoritative, append-only evaluation ledger)
CREATE TABLE evidence_records (
    evidence_id BIGSERIAL PRIMARY KEY,
    attempt_id UUID NOT NULL UNIQUE,
    session_id UUID NOT NULL,
    learner_id VARCHAR(64) NOT NULL,
    topic_id VARCHAR(64) NOT NULL,
    question_id VARCHAR(64) NOT NULL,
    question_type VARCHAR(32) NOT NULL,
    difficulty VARCHAR(16) NOT NULL,
    hints_used INT NOT NULL DEFAULT 0,
    is_retrieval BOOLEAN NOT NULL DEFAULT FALSE,
    evaluation_result VARCHAR(32) NOT NULL,
    misconception_category VARCHAR(64) NULL,
    feedback TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Composite Foreign Key strictly binding evidence to the issued attempt context:
    CONSTRAINT fk_evidence_attempt_context
        FOREIGN KEY (attempt_id, session_id, learner_id, topic_id, question_id)
        REFERENCES attempts(attempt_id, session_id, learner_id, topic_id, question_id)
        ON DELETE CASCADE,

    -- Direct session reference:
    CONSTRAINT fk_evidence_session
        FOREIGN KEY (session_id)
        REFERENCES learning_sessions(session_id)
        ON DELETE CASCADE,

    -- Direct learner reference:
    CONSTRAINT fk_evidence_learner
        FOREIGN KEY (learner_id)
        REFERENCES learners(learner_id)
        ON DELETE CASCADE,

    -- Evaluation result constraint:
    CONSTRAINT chk_evidence_result CHECK (evaluation_result IN ('PASS', 'FAIL', 'PARTIAL', 'HINT', 'INVALID_RESPONSE'))
);

-- Fast deterministic chronological replay index:
CREATE INDEX idx_evidence_learner_topic_chronological
    ON evidence_records(learner_id, topic_id, created_at ASC, evidence_id ASC);

-- 5. Topic Mastery Snapshots (Materialized cache of derived state)
CREATE TABLE topic_mastery_snapshots (
    learner_id VARCHAR(64) NOT NULL REFERENCES learners(learner_id) ON DELETE CASCADE,
    topic_id VARCHAR(64) NOT NULL,
    mastery_level VARCHAR(24) NOT NULL DEFAULT 'NOVICE',
    independent_concept_passes INT NOT NULL DEFAULT 0,
    independent_implementation_passes INT NOT NULL DEFAULT 0,
    transfer_passes INT NOT NULL DEFAULT 0,
    retrieval_passes INT NOT NULL DEFAULT 0,
    consecutive_failures INT NOT NULL DEFAULT 0,
    active_misconceptions JSONB NOT NULL DEFAULT '[]'::jsonb,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (learner_id, topic_id)
);
