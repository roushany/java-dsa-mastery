package com.javadsamastery.infrastructure.persistence.adapter;

import com.javadsamastery.domain.learner.LearnerProfile;
import com.javadsamastery.domain.ports.LearnerRepositoryPort;
import com.javadsamastery.infrastructure.persistence.entity.LearnerEntity;
import com.javadsamastery.infrastructure.persistence.repository.SpringDataLearnerRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "javadsa.persistence.enabled", havingValue = "true")
public class JpaLearnerRepositoryAdapter implements LearnerRepositoryPort {

    private final SpringDataLearnerRepository repository;

    public JpaLearnerRepositoryAdapter(SpringDataLearnerRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LearnerProfile> findById(String learnerId) {
        return repository.findById(learnerId)
            .map(entity -> new LearnerProfile(entity.getLearnerId()));
    }

    @Override
    @Transactional
    public void save(String learnerId, boolean isAnonymous) {
        LearnerEntity entity = repository.findById(learnerId)
            .orElseGet(() -> new LearnerEntity(learnerId, isAnonymous, Instant.now(), Instant.now()));
        entity.setLastActiveAt(Instant.now());
        repository.save(entity);
    }

    @Override
    @Transactional
    public void deleteById(String learnerId) {
        repository.deleteById(learnerId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(String learnerId) {
        return repository.existsById(learnerId);
    }
}
