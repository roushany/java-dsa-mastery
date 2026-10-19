package com.javadsamastery.infrastructure.persistence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javadsamastery.domain.ports.AttemptRepositoryPort;
import com.javadsamastery.infrastructure.persistence.entity.AttemptEntity;
import com.javadsamastery.infrastructure.persistence.mapper.AttemptEntityMapper;
import com.javadsamastery.infrastructure.persistence.repository.SpringDataAttemptRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "javadsa.persistence.enabled", havingValue = "true")
public class JpaAttemptRepositoryAdapter implements AttemptRepositoryPort {

    private final SpringDataAttemptRepository repository;
    private final AttemptEntityMapper mapper;

    public JpaAttemptRepositoryAdapter(SpringDataAttemptRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.mapper = new AttemptEntityMapper(objectMapper);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AttemptRecord> findById(UUID attemptId) {
        return repository.findById(attemptId).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AttemptRecord> findActiveAttemptBySessionId(UUID sessionId) {
        return repository.findBySessionIdAndStatus(sessionId, "ISSUED").map(mapper::toDomain);
    }

    @Override
    @Transactional
    public AttemptRecord save(AttemptRecord attemptRecord) {
        AttemptEntity entity = mapper.toEntity(attemptRecord);
        AttemptEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional
    public boolean consumeAttempt(UUID attemptId) {
        int rowsUpdated = repository.consumeAttempt(attemptId, Instant.now());
        return rowsUpdated > 0;
    }

    @Override
    @Transactional
    public int expireIssuedAttemptsForExpiredSessions(Instant now) {
        return repository.expireIssuedAttemptsForExpiredSessions(now);
    }
}
