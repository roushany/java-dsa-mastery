package com.javadsamastery.infrastructure.persistence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javadsamastery.domain.ports.TopicMasteryRepositoryPort;
import com.javadsamastery.infrastructure.persistence.entity.TopicMasterySnapshotEntity;
import com.javadsamastery.infrastructure.persistence.entity.TopicMasterySnapshotId;
import com.javadsamastery.infrastructure.persistence.mapper.TopicMasterySnapshotMapper;
import com.javadsamastery.infrastructure.persistence.repository.SpringDataTopicMasteryRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "javadsa.persistence.enabled", havingValue = "true")
public class JpaTopicMasteryRepositoryAdapter implements TopicMasteryRepositoryPort {

    private final SpringDataTopicMasteryRepository repository;
    private final TopicMasterySnapshotMapper mapper;

    public JpaTopicMasteryRepositoryAdapter(SpringDataTopicMasteryRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.mapper = new TopicMasterySnapshotMapper(objectMapper);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MasterySnapshotRecord> findSnapshot(String learnerId, String topicId) {
        return repository.findById(new TopicMasterySnapshotId(learnerId, topicId)).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public MasterySnapshotRecord saveSnapshot(MasterySnapshotRecord snapshot) {
        TopicMasterySnapshotEntity entity = mapper.toEntity(snapshot);
        TopicMasterySnapshotEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    /**
     * Safeguard implementation: Handles safe initialization and row-level locking for both
     * existing and nonexistent (learner_id, topic_id) mastery snapshots.
     * Prevents concurrency lost-updates without relying on SELECT FOR UPDATE on nonexistent rows.
     */
    @Override
    @Transactional
    public MasterySnapshotRecord lockOrInitialize(String learnerId, String topicId) {
        // Step 1: Ensure row exists via native ON CONFLICT DO NOTHING (race-safe, zero transaction abort risk)
        repository.insertInitialIfAbsent(learnerId, topicId);

        // Step 2: Lock the row with SELECT FOR UPDATE
        TopicMasterySnapshotEntity locked = repository.findByLearnerIdAndTopicIdForUpdate(learnerId, topicId)
            .orElseThrow(() -> new IllegalStateException(
                "Failed to lock topic mastery snapshot after initialization for: " + learnerId + "/" + topicId));
        return mapper.toDomain(locked);
    }

    @Override
    @Transactional
    public void deleteByLearnerIdAndTopicId(String learnerId, String topicId) {
        repository.deleteById(new TopicMasterySnapshotId(learnerId, topicId));
    }
}
