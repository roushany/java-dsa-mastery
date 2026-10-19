package com.javadsamastery.domain.ports;

import com.javadsamastery.domain.evidence.AttemptContext;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound repository port for question attempt token management.
 * Pure domain types only.
 */
public interface AttemptRepositoryPort {

    record AttemptRecord(
        AttemptContext context,
        UUID sessionId,
        String expectedEvidence,
        int hintsRequested,
        String status,
        Instant createdAt,
        Instant consumedAt
    ) {}

    Optional<AttemptRecord> findById(UUID attemptId);
    Optional<AttemptRecord> findActiveAttemptBySessionId(UUID sessionId);
    AttemptRecord save(AttemptRecord attemptRecord);
    boolean consumeAttempt(UUID attemptId);
    int expireIssuedAttemptsForExpiredSessions(Instant now);
}
