package com.javadsamastery.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evidence_records")
public class EvidenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "evidence_id", nullable = false)
    private Long evidenceId;

    @Column(name = "attempt_id", nullable = false, unique = true)
    private UUID attemptId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "learner_id", length = 64, nullable = false)
    private String learnerId;

    @Column(name = "topic_id", length = 64, nullable = false)
    private String topicId;

    @Column(name = "question_id", length = 64, nullable = false)
    private String questionId;

    @Column(name = "question_type", length = 32, nullable = false)
    private String questionType;

    @Column(name = "difficulty", length = 16, nullable = false)
    private String difficulty;

    @Column(name = "hints_used", nullable = false)
    private int hintsUsed;

    @Column(name = "is_retrieval", nullable = false)
    private boolean isRetrieval;

    @Column(name = "evaluation_result", length = 32, nullable = false)
    private String evaluationResult;

    @Column(name = "misconception_category", length = 64)
    private String misconceptionCategory;

    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "idempotency_fingerprint", length = 64)
    private String idempotencyFingerprint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected EvidenceEntity() {}

    public EvidenceEntity(
            UUID attemptId,
            UUID sessionId,
            String learnerId,
            String topicId,
            String questionId,
            String questionType,
            String difficulty,
            int hintsUsed,
            boolean isRetrieval,
            String evaluationResult,
            String misconceptionCategory,
            String feedback,
            Instant createdAt) {
        this(attemptId, sessionId, learnerId, topicId, questionId, questionType, difficulty,
             hintsUsed, isRetrieval, evaluationResult, misconceptionCategory, feedback, null, createdAt);
    }

    public EvidenceEntity(
            UUID attemptId,
            UUID sessionId,
            String learnerId,
            String topicId,
            String questionId,
            String questionType,
            String difficulty,
            int hintsUsed,
            boolean isRetrieval,
            String evaluationResult,
            String misconceptionCategory,
            String feedback,
            String idempotencyFingerprint,
            Instant createdAt) {
        this.attemptId = attemptId;
        this.sessionId = sessionId;
        this.learnerId = learnerId;
        this.topicId = topicId;
        this.questionId = questionId;
        this.questionType = questionType;
        this.difficulty = difficulty;
        this.hintsUsed = hintsUsed;
        this.isRetrieval = isRetrieval;
        this.evaluationResult = evaluationResult;
        this.misconceptionCategory = misconceptionCategory;
        this.feedback = feedback;
        this.idempotencyFingerprint = idempotencyFingerprint;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public Long getEvidenceId() { return evidenceId; }
    public void setEvidenceId(Long evidenceId) { this.evidenceId = evidenceId; }

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

    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public int getHintsUsed() { return hintsUsed; }
    public void setHintsUsed(int hintsUsed) { this.hintsUsed = hintsUsed; }

    public boolean isRetrieval() { return isRetrieval; }
    public void setRetrieval(boolean retrieval) { isRetrieval = retrieval; }

    public String getEvaluationResult() { return evaluationResult; }
    public void setEvaluationResult(String evaluationResult) { this.evaluationResult = evaluationResult; }

    public String getMisconceptionCategory() { return misconceptionCategory; }
    public void setMisconceptionCategory(String misconceptionCategory) { this.misconceptionCategory = misconceptionCategory; }

    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }

    public String getIdempotencyFingerprint() { return idempotencyFingerprint; }
    public void setIdempotencyFingerprint(String idempotencyFingerprint) { this.idempotencyFingerprint = idempotencyFingerprint; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
