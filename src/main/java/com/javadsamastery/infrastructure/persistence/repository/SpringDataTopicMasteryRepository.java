package com.javadsamastery.infrastructure.persistence.repository;

import com.javadsamastery.infrastructure.persistence.entity.TopicMasterySnapshotEntity;
import com.javadsamastery.infrastructure.persistence.entity.TopicMasterySnapshotId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpringDataTopicMasteryRepository extends JpaRepository<TopicMasterySnapshotEntity, TopicMasterySnapshotId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM TopicMasterySnapshotEntity s WHERE s.id.learnerId = :learnerId AND s.id.topicId = :topicId")
    Optional<TopicMasterySnapshotEntity> findByLearnerIdAndTopicIdForUpdate(
            @Param("learnerId") String learnerId,
            @Param("topicId") String topicId);

    @org.springframework.data.jpa.repository.Modifying
    @Query(value = "INSERT INTO javadsa.topic_mastery_snapshots (learner_id, topic_id, mastery_level, independent_concept_passes, " +
                   "independent_implementation_passes, transfer_passes, retrieval_passes, consecutive_failures, " +
                   "active_misconceptions, updated_at, version) " +
                   "VALUES (:learnerId, :topicId, 'NOVICE', 0, 0, 0, 0, 0, '[]'::jsonb, NOW(), 0) " +
                   "ON CONFLICT (learner_id, topic_id) DO NOTHING", nativeQuery = true)
    int insertInitialIfAbsent(@Param("learnerId") String learnerId, @Param("topicId") String topicId);
}
