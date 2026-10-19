package com.javadsamastery.domain.ports;

import com.javadsamastery.domain.learner.LearnerProfile;
import java.util.Optional;

/**
 * Outbound repository port for learner identity operations.
 * Strictly decoupled from persistence frameworks.
 */
public interface LearnerRepositoryPort {
    Optional<LearnerProfile> findById(String learnerId);
    void save(String learnerId, boolean isAnonymous);
    void deleteById(String learnerId);
    boolean existsById(String learnerId);
}
