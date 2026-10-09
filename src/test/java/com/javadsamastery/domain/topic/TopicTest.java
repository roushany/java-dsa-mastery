package com.javadsamastery.domain.topic;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TopicTest {
    @Test
    void testValidTopic() {
        Topic topic = new Topic("arrays", "Arrays", "Basics of arrays");
        assertEquals("arrays", topic.id());
        assertEquals("Arrays", topic.name());
        assertEquals("Basics of arrays", topic.description());
    }

    @Test
    void testInvalidTopicId() {
        assertThrows(IllegalArgumentException.class, () -> new Topic("", "Name", "Desc"));
        assertThrows(IllegalArgumentException.class, () -> new Topic(null, "Name", "Desc"));
    }

    @Test
    void testInvalidTopicName() {
        assertThrows(IllegalArgumentException.class, () -> new Topic("id", "", "Desc"));
    }
}
