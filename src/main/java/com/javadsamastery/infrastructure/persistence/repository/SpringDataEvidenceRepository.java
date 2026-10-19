package com.javadsamastery.infrastructure.persistence.repository;

import com.javadsamastery.infrastructure.persistence.entity.EvidenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataEvidenceRepository extends JpaRepository<EvidenceEntity, Long> {

    List<EvidenceEntity> findByLearnerIdAndTopicIdOrderByCreatedAtAscEvidenceIdAsc(String learnerId, String topicId);

    Optional<EvidenceEntity> findByAttemptId(UUID attemptId);

    long countByLearnerIdAndTopicId(String learnerId, String topicId);
}
