package com.javadsamastery.infrastructure.persistence.adapter;

import com.javadsamastery.domain.ports.SessionRepositoryPort;
import com.javadsamastery.infrastructure.persistence.entity.SessionEntity;
import com.javadsamastery.infrastructure.persistence.mapper.SessionEntityMapper;
import com.javadsamastery.infrastructure.persistence.repository.SpringDataSessionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "javadsa.persistence.enabled", havingValue = "true")
public class JpaSessionRepositoryAdapter implements SessionRepositoryPort {

    private final SpringDataSessionRepository repository;
    private final SessionEntityMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    @org.springframework.beans.factory.annotation.Autowired
    public JpaSessionRepositoryAdapter(SpringDataSessionRepository repository) {
        this.repository = repository;
        this.mapper = new SessionEntityMapper();
    }

    public JpaSessionRepositoryAdapter(SpringDataSessionRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.mapper = new SessionEntityMapper();
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SessionSnapshot> findById(UUID sessionId) {
        return repository.findById(sessionId).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public SessionSnapshot save(SessionSnapshot snapshot) {
        SessionEntity entity = mapper.toEntity(snapshot);
        if (entity.getSessionId() != null && !repository.existsById(entity.getSessionId())) {
            entity.setVersion(null);
            if (entityManager != null) {
                entityManager.persist(entity);
                return mapper.toDomain(entity);
            }
        }
        SessionEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional
    public void deleteById(UUID sessionId) {
        repository.deleteById(sessionId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(UUID sessionId) {
        return repository.existsById(sessionId);
    }

    @Override
    @Transactional
    public int expireSessions(java.time.Instant now) {
        return repository.expireSessions(now);
    }
}
