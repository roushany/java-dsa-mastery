package com.javadsamastery.domain.learner;

import com.javadsamastery.domain.topic.TopicMastery;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LearnerProfileTest {
    @Test
    void testInitialState() {
        LearnerProfile profile = new LearnerProfile("l1");
        assertEquals("l1", profile.getLearnerId());
        assertEquals(TopicMastery.NOVICE, profile.getMastery("arrays"));
    }

    @Test
    void testTopicStateIntegration() {
        LearnerProfile profile = new LearnerProfile("l1");
        assertNotNull(profile.getTopicState("arrays"));
        assertEquals(TopicMastery.NOVICE, profile.getMastery("arrays"));
    }
}
