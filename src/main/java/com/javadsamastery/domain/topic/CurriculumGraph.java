package com.javadsamastery.domain.topic;

import java.util.*;

/**
 * Manages the prerequisite dependencies between topics.
 */
public class CurriculumGraph {
    private final Map<String, Set<String>> adjacencyList = new HashMap<>();
    private final Map<String, com.javadsamastery.domain.curriculum.TopicDefinition> definitions = new HashMap<>();

    public void addDependency(String topicId, String prerequisiteId) {
        if (topicId == null) {
            throw new IllegalArgumentException("Topic cannot be null");
        }
        adjacencyList.putIfAbsent(topicId, new HashSet<>());
        if (prerequisiteId != null) {
            adjacencyList.get(topicId).add(prerequisiteId);
        }
    }
    
    public void addDefinition(com.javadsamastery.domain.curriculum.TopicDefinition def) {
        definitions.put(def.topicId(), def);
    }
    
    public com.javadsamastery.domain.curriculum.TopicDefinition getDefinition(String topicId) {
        return definitions.get(topicId);
    }

    public Set<String> getPrerequisites(String topicId) {
        return adjacencyList.getOrDefault(topicId, Collections.emptySet());
    }

    public boolean canAccess(String topicId, Set<String> masteredTopicIds) {
        Set<String> required = getPrerequisites(topicId);
        return masteredTopicIds.containsAll(required);
    }
}
