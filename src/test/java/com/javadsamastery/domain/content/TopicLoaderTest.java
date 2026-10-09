package com.javadsamastery.domain.content;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TopicLoaderTest {

    @Test
    void testValidTopicLoads() {
        TopicTemplate template = TopicLoader.load("linear-search");
        assertNotNull(template);
        assertEquals("linear-search", template.topicId());
        assertEquals("Linear Search", template.title());
        assertNotNull(template.intuition());
    }

    @Test
    void testBinarySearchLoadsCorrectly() {
        TopicTemplate template = TopicLoader.load("binary-search");
        assertNotNull(template);
        assertEquals("binary-search", template.topicId());
        assertEquals("Binary Search", template.title());
        assertNotNull(template.complexity());
    }

    @Test
    void testInvalidTopicIdentifierFails() {
        assertThrows(IllegalArgumentException.class, () -> TopicLoader.load(""));
        assertThrows(IllegalArgumentException.class, () -> TopicLoader.load(null));
    }

    @Test
    void testMissingResourceFailsClearly() {
        Exception e = assertThrows(IllegalArgumentException.class, () -> TopicLoader.load("non-existent-topic"));
        assertTrue(e.getMessage().contains("Topic resource not found"));
    }
    
    // Testing missing required field or malformed content would require mocking the resource stream,
    // but the logic relies on Jackson and simple if-null checks. We can rely on unit testing the validate method implicitly via standard loading.
}
