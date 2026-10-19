package com.javadsamastery.domain.ports;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound repository port for session persistence.
 * Zero framework dependencies.
 */
public interface SessionRepositoryPort {

    record SessionSnapshot(
        UUID sessionId,
        String learnerId,
        String topicId,
        String currentState,
        int nominalStageIndex,
        int pedagogicalFailures,
        int totalAttemptsInState,
        boolean isCompleted,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt
    ) {}

    Optional<SessionSnapshot> findById(UUID sessionId);
    SessionSnapshot save(SessionSnapshot snapshot);
    void deleteById(UUID sessionId);
    boolean existsById(UUID sessionId);
    int expireSessions(Instant now);
}
