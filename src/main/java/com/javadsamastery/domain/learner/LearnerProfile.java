package com.javadsamastery.domain.learner;

import com.javadsamastery.domain.topic.TopicMastery;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Root entity representing a user's state and mastery across topics.
 * Contains current mastery maps.
 */
public class LearnerProfile {
    private final String learnerId;
    private final Map<String, TopicState> topicStateMap;

    public LearnerProfile(String learnerId) {
        if (learnerId == null || learnerId.isBlank()) throw new IllegalArgumentException("Learner ID cannot be blank");
        this.learnerId = learnerId;
        this.topicStateMap = new HashMap<>();
    }

    public String getLearnerId() {
        return learnerId;
    }
    
    public TopicState getTopicState(String topicId) {
        return topicStateMap.computeIfAbsent(topicId, TopicState::new);
    }

    public TopicMastery getMastery(String topicId) {
        return getTopicState(topicId).getMastery();
    }


}
