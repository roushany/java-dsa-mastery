package com.javadsamastery.infrastructure.persistence.repository;

import com.javadsamastery.infrastructure.persistence.entity.SessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataSessionRepository extends JpaRepository<SessionEntity, UUID> {
    List<SessionEntity> findByLearnerIdAndTopicId(String learnerId, String topicId);

    @Modifying
    @Query("UPDATE SessionEntity s SET s.isCompleted = true, s.updatedAt = :now WHERE s.expiresAt < :now AND s.isCompleted = false")
    int expireSessions(@Param("now") Instant now);
}
