package com.javadsamastery.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "learners")
public class LearnerEntity {

    @Id
    @Column(name = "learner_id", length = 64, nullable = false)
    private String learnerId;

    @Column(name = "is_anonymous", nullable = false)
    private boolean isAnonymous;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_active_at", nullable = false)
    private Instant lastActiveAt;

    protected LearnerEntity() {}

    public LearnerEntity(String learnerId, boolean isAnonymous, Instant createdAt, Instant lastActiveAt) {
        this.learnerId = learnerId;
        this.isAnonymous = isAnonymous;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.lastActiveAt = lastActiveAt != null ? lastActiveAt : Instant.now();
    }

    public String getLearnerId() { return learnerId; }
    public void setLearnerId(String learnerId) { this.learnerId = learnerId; }

    public boolean isAnonymous() { return isAnonymous; }
    public void setAnonymous(boolean anonymous) { isAnonymous = anonymous; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(Instant lastActiveAt) { this.lastActiveAt = lastActiveAt; }
}
