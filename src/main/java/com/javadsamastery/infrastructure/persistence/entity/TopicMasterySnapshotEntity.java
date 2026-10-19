package com.javadsamastery.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "topic_mastery_snapshots")
public class TopicMasterySnapshotEntity {

    @EmbeddedId
    private TopicMasterySnapshotId id;

    @Column(name = "mastery_level", length = 24, nullable = false)
    private String masteryLevel;

    @Column(name = "independent_concept_passes", nullable = false)
    private int independentConceptPasses;

    @Column(name = "independent_implementation_passes", nullable = false)
    private int independentImplementationPasses;

    @Column(name = "transfer_passes", nullable = false)
    private int transferPasses;

    @Column(name = "retrieval_passes", nullable = false)
    private int retrievalPasses;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "active_misconceptions", nullable = false)
    private String activeMisconceptions;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected TopicMasterySnapshotEntity() {}

    public TopicMasterySnapshotEntity(
            String learnerId,
            String topicId,
            String masteryLevel,
            int independentConceptPasses,
            int independentImplementationPasses,
            int transferPasses,
            int retrievalPasses,
            int consecutiveFailures,
            String activeMisconceptions,
            Instant updatedAt,
            Long version) {
        this.id = new TopicMasterySnapshotId(learnerId, topicId);
        this.masteryLevel = masteryLevel != null ? masteryLevel : "NOVICE";
        this.independentConceptPasses = independentConceptPasses;
        this.independentImplementationPasses = independentImplementationPasses;
        this.transferPasses = transferPasses;
        this.retrievalPasses = retrievalPasses;
        this.consecutiveFailures = consecutiveFailures;
        this.activeMisconceptions = activeMisconceptions != null ? activeMisconceptions : "[]";
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
        this.version = version != null ? version : 0L;
    }

    public TopicMasterySnapshotId getId() { return id; }
    public void setId(TopicMasterySnapshotId id) { this.id = id; }

    public String getLearnerId() { return id != null ? id.getLearnerId() : null; }
    public String getTopicId() { return id != null ? id.getTopicId() : null; }

    public String getMasteryLevel() { return masteryLevel; }
    public void setMasteryLevel(String masteryLevel) { this.masteryLevel = masteryLevel; }

    public int getIndependentConceptPasses() { return independentConceptPasses; }
    public void setIndependentConceptPasses(int independentConceptPasses) { this.independentConceptPasses = independentConceptPasses; }

    public int getIndependentImplementationPasses() { return independentImplementationPasses; }
    public void setIndependentImplementationPasses(int independentImplementationPasses) { this.independentImplementationPasses = independentImplementationPasses; }

    public int getTransferPasses() { return transferPasses; }
    public void setTransferPasses(int transferPasses) { this.transferPasses = transferPasses; }

    public int getRetrievalPasses() { return retrievalPasses; }
    public void setRetrievalPasses(int retrievalPasses) { this.retrievalPasses = retrievalPasses; }

    public int getConsecutiveFailures() { return consecutiveFailures; }
    public void setConsecutiveFailures(int consecutiveFailures) { this.consecutiveFailures = consecutiveFailures; }

    public String getActiveMisconceptions() { return activeMisconceptions; }
    public void setActiveMisconceptions(String activeMisconceptions) { this.activeMisconceptions = activeMisconceptions; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
