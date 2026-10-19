package com.javadsamastery.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "learning_sessions")
public class SessionEntity {

    @Id
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "learner_id", length = 64, nullable = false)
    private String learnerId;

    @Column(name = "topic_id", length = 64, nullable = false)
    private String topicId;

    @Column(name = "current_state", length = 32, nullable = false)
    private String currentState;

    @Column(name = "nominal_stage_index", nullable = false)
    private int nominalStageIndex;

    @Column(name = "pedagogical_failures", nullable = false)
    private int pedagogicalFailures;

    @Column(name = "total_attempts_in_state", nullable = false)
    private int totalAttemptsInState;

    @Column(name = "is_completed", nullable = false)
    private boolean isCompleted;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected SessionEntity() {}

    public SessionEntity(
            UUID sessionId,
            String learnerId,
            String topicId,
            String currentState,
            int nominalStageIndex,
            int pedagogicalFailures,
            int totalAttemptsInState,
            boolean isCompleted,
            Long version,
            Instant createdAt,
            Instant updatedAt,
            Instant expiresAt) {
        this.sessionId = sessionId;
        this.learnerId = learnerId;
        this.topicId = topicId;
        this.currentState = currentState;
        this.nominalStageIndex = nominalStageIndex;
        this.pedagogicalFailures = pedagogicalFailures;
        this.totalAttemptsInState = totalAttemptsInState;
        this.isCompleted = isCompleted;
        this.version = version != null ? version : 0L;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
        this.expiresAt = expiresAt != null ? expiresAt : Instant.now().plusSeconds(86400);
    }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }

    public String getLearnerId() { return learnerId; }
    public void setLearnerId(String learnerId) { this.learnerId = learnerId; }

    public String getTopicId() { return topicId; }
    public void setTopicId(String topicId) { this.topicId = topicId; }

    public String getCurrentState() { return currentState; }
    public void setCurrentState(String currentState) { this.currentState = currentState; }

    public int getNominalStageIndex() { return nominalStageIndex; }
    public void setNominalStageIndex(int nominalStageIndex) { this.nominalStageIndex = nominalStageIndex; }

    public int getPedagogicalFailures() { return pedagogicalFailures; }
    public void setPedagogicalFailures(int pedagogicalFailures) { this.pedagogicalFailures = pedagogicalFailures; }

    public int getTotalAttemptsInState() { return totalAttemptsInState; }
    public void setTotalAttemptsInState(int totalAttemptsInState) { this.totalAttemptsInState = totalAttemptsInState; }

    public boolean isCompleted() { return isCompleted; }
    public void setCompleted(boolean completed) { isCompleted = completed; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
