package com.javadsamastery.domain.ports;

import com.javadsamastery.domain.evidence.EvidenceRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound repository port for authoritative evidence records.
 * Provides chronological access for deterministic mastery re-evaluation.
 */
public interface EvidenceRepositoryPort {

    record PersistedEvidence(
        Long evidenceId,
        EvidenceRecord evidenceRecord,
        UUID attemptId,
        UUID sessionId,
        String feedback,
        String idempotencyFingerprint
    ) {
        public PersistedEvidence(Long evidenceId, EvidenceRecord evidenceRecord, UUID attemptId, UUID sessionId, String feedback) {
            this(evidenceId, evidenceRecord, attemptId, sessionId, feedback, null);
        }
    }

    PersistedEvidence save(EvidenceRecord evidence, UUID attemptId, UUID sessionId, String feedback, String idempotencyFingerprint);

    default PersistedEvidence save(EvidenceRecord evidence, UUID attemptId, UUID sessionId, String feedback) {
        return save(evidence, attemptId, sessionId, feedback, null);
    }
    List<EvidenceRecord> findChronologicalHistory(String learnerId, String topicId);
    Optional<PersistedEvidence> findByAttemptId(UUID attemptId);
    long countByLearnerIdAndTopicId(String learnerId, String topicId);
}
