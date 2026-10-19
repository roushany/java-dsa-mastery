package com.javadsamastery.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attempts")
public class AttemptEntity {

    @Id
    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "learner_id", length = 64, nullable = false)
    private String learnerId;

    @Column(name = "topic_id", length = 64, nullable = false)
    private String topicId;

    @Column(name = "question_id", length = 64, nullable = false)
    private String questionId;

    @Column(name = "target_stage", length = 32, nullable = false)
    private String targetStage;

    @Column(name = "question_type", length = 32, nullable = false)
    private String questionType;

    @Column(name = "difficulty", length = 16, nullable = false)
    private String difficulty;

    @Column(name = "expected_evidence", columnDefinition = "TEXT", nullable = false)
    private String expectedEvidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_misconceptions", nullable = false)
    private String allowedMisconceptions;

    @Column(name = "hints_requested", nullable = false)
    private int hintsRequested;

    @Column(name = "status", length = 24, nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected AttemptEntity() {}

    public AttemptEntity(
            UUID attemptId,
            UUID sessionId,
            String learnerId,
            String topicId,
            String questionId,
            String targetStage,
            String questionType,
            String difficulty,
            String expectedEvidence,
            String allowedMisconceptions,
            int hintsRequested,
            String status,
            Instant createdAt,
            Instant consumedAt) {
        this.attemptId = attemptId;
        this.sessionId = sessionId;
        this.learnerId = learnerId;
        this.topicId = topicId;
        this.questionId = questionId;
        this.targetStage = targetStage;
        this.questionType = questionType;
        this.difficulty = difficulty;
        this.expectedEvidence = expectedEvidence;
        this.allowedMisconceptions = allowedMisconceptions != null ? allowedMisconceptions : "[]";
        this.hintsRequested = hintsRequested;
        this.status = status != null ? status : "ISSUED";
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.consumedAt = consumedAt;
    }

    public UUID getAttemptId() { return attemptId; }
    public void setAttemptId(UUID attemptId) { this.attemptId = attemptId; }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }

    public String getLearnerId() { return learnerId; }
    public void setLearnerId(String learnerId) { this.learnerId = learnerId; }

    public String getTopicId() { return topicId; }
    public void setTopicId(String topicId) { this.topicId = topicId; }

    public String getQuestionId() { return questionId; }
    public void setQuestionId(String questionId) { this.questionId = questionId; }

    public String getTargetStage() { return targetStage; }
    public void setTargetStage(String targetStage) { this.targetStage = targetStage; }

    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public String getExpectedEvidence() { return expectedEvidence; }
    public void setExpectedEvidence(String expectedEvidence) { this.expectedEvidence = expectedEvidence; }

    public String getAllowedMisconceptions() { return allowedMisconceptions; }
    public void setAllowedMisconceptions(String allowedMisconceptions) { this.allowedMisconceptions = allowedMisconceptions; }

    public int getHintsRequested() { return hintsRequested; }
    public void setHintsRequested(int hintsRequested) { this.hintsRequested = hintsRequested; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getConsumedAt() { return consumedAt; }
    public void setConsumedAt(Instant consumedAt) { this.consumedAt = consumedAt; }
}
