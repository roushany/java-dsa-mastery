package com.javadsamastery.domain.topic;

/**
 * Represents a conceptual area of study in Data Structures and Algorithms.
 * Immutable structure.
 */
public record Topic(String id, String name, String description) {
    public Topic {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Topic ID cannot be null or blank");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Topic name cannot be null or blank");
    }
}
