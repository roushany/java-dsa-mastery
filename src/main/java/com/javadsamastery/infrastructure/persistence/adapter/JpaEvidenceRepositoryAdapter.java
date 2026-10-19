package com.javadsamastery.infrastructure.persistence.adapter;

import com.javadsamastery.domain.evidence.EvidenceRecord;
import com.javadsamastery.domain.ports.EvidenceRepositoryPort;
import com.javadsamastery.infrastructure.persistence.entity.EvidenceEntity;
import com.javadsamastery.infrastructure.persistence.mapper.EvidenceEntityMapper;
import com.javadsamastery.infrastructure.persistence.repository.SpringDataEvidenceRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "javadsa.persistence.enabled", havingValue = "true")
public class JpaEvidenceRepositoryAdapter implements EvidenceRepositoryPort {

    private final SpringDataEvidenceRepository repository;
    private final EvidenceEntityMapper mapper;

    public JpaEvidenceRepositoryAdapter(SpringDataEvidenceRepository repository) {
        this.repository = repository;
        this.mapper = new EvidenceEntityMapper();
    }

    @Override
    @Transactional
    public PersistedEvidence save(EvidenceRecord evidence, UUID attemptId, UUID sessionId, String feedback, String idempotencyFingerprint) {
        EvidenceEntity entity = mapper.toEntity(evidence, attemptId, sessionId, feedback, idempotencyFingerprint);
        EvidenceEntity saved = repository.save(entity);
        return mapper.toPersistedDomain(saved);
    }

    @Override
    @Transactional
    public PersistedEvidence save(EvidenceRecord evidence, UUID attemptId, UUID sessionId, String feedback) {
        return save(evidence, attemptId, sessionId, feedback, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvidenceRecord> findChronologicalHistory(String learnerId, String topicId) {
        return repository.findByLearnerIdAndTopicIdOrderByCreatedAtAscEvidenceIdAsc(learnerId, topicId)
            .stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PersistedEvidence> findByAttemptId(UUID attemptId) {
        return repository.findByAttemptId(attemptId).map(mapper::toPersistedDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByLearnerIdAndTopicId(String learnerId, String topicId) {
        return repository.countByLearnerIdAndTopicId(learnerId, topicId);
    }
}
