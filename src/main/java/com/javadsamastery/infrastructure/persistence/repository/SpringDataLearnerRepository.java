package com.javadsamastery.infrastructure.persistence.repository;

import com.javadsamastery.infrastructure.persistence.entity.LearnerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataLearnerRepository extends JpaRepository<LearnerEntity, String> {
}
