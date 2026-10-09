package com.javadsamastery.domain.problem;

import java.util.Set;

/**
 * Represents a specific algorithmic challenge.
 * Immutable structure.
 */
public record Problem(String id, String title, ProblemDifficulty difficulty, Set<String> topicIds) {
    public Problem {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Problem ID cannot be blank");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Problem title cannot be blank");
        if (topicIds == null || topicIds.isEmpty()) throw new IllegalArgumentException("Problem must belong to at least one topic");
        topicIds = Set.copyOf(topicIds); // Ensure immutability
    }
}
