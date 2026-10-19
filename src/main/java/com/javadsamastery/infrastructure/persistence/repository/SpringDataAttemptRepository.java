package com.javadsamastery.infrastructure.persistence.repository;

import com.javadsamastery.infrastructure.persistence.entity.AttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataAttemptRepository extends JpaRepository<AttemptEntity, UUID> {

    Optional<AttemptEntity> findBySessionIdAndStatus(UUID sessionId, String status);

    @Modifying
    @Query("UPDATE AttemptEntity a SET a.status = 'CONSUMED', a.consumedAt = :consumedAt WHERE a.attemptId = :attemptId AND a.status = 'ISSUED'")
    int consumeAttempt(@Param("attemptId") UUID attemptId, @Param("consumedAt") Instant consumedAt);

    @Modifying
    @Query("UPDATE AttemptEntity a SET a.status = 'EXPIRED', a.consumedAt = :now WHERE a.sessionId IN (SELECT s.sessionId FROM SessionEntity s WHERE s.expiresAt < :now) AND a.status = 'ISSUED'")
    int expireIssuedAttemptsForExpiredSessions(@Param("now") Instant now);
}
