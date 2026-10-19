package com.javadsamastery.domain.ports;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Outbound repository port for derived topic mastery snapshots.
 * Supports safe creation, locking, and reconciliation.
 */
public interface TopicMasteryRepositoryPort {

    record MasterySnapshotRecord(
        String learnerId,
        String topicId,
        String masteryLevel,
        int independentConceptPasses,
        int independentImplementationPasses,
        int transferPasses,
        int retrievalPasses,
        int consecutiveFailures,
        Set<String> activeMisconceptions,
        Instant updatedAt,
        long version
    ) {}

    Optional<MasterySnapshotRecord> findSnapshot(String learnerId, String topicId);
    MasterySnapshotRecord saveSnapshot(MasterySnapshotRecord snapshot);
    MasterySnapshotRecord lockOrInitialize(String learnerId, String topicId);
    void deleteByLearnerIdAndTopicId(String learnerId, String topicId);
}
