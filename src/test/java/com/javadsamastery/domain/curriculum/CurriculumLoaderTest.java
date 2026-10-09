package com.javadsamastery.domain.curriculum;

import com.javadsamastery.domain.topic.CurriculumGraph;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CurriculumLoaderTest {

    private java.io.InputStream createStream(String json) {
        return new java.io.ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void testValidCurriculum() {
        String json = """
            {
              "topics": [
                { "topicId": "A", "title": "A", "prerequisites": [] },
                { "topicId": "B", "title": "B", "prerequisites": ["A"] }
              ]
            }
            """;
        CurriculumGraph graph = CurriculumLoader.loadCurriculum(createStream(json));
        assertNotNull(graph);
        assertTrue(graph.getPrerequisites("B").contains("A"));
    }

    @Test
    void testDuplicateTopicId() {
        String json = """
            {
              "topics": [
                { "topicId": "A", "title": "A", "prerequisites": [] },
                { "topicId": "A", "title": "A2", "prerequisites": [] }
              ]
            }
            """;
        Exception e = assertThrows(IllegalArgumentException.class, () -> CurriculumLoader.loadCurriculum(createStream(json)));
        assertTrue(e.getMessage().contains("Duplicate topic ID"));
    }

    @Test
    void testUnknownPrerequisite() {
        String json = """
            {
              "topics": [
                { "topicId": "A", "title": "A", "prerequisites": ["X"] }
              ]
            }
            """;
        Exception e = assertThrows(IllegalArgumentException.class, () -> CurriculumLoader.loadCurriculum(createStream(json)));
        assertTrue(e.getMessage().contains("Unknown prerequisite 'X'"));
    }

    @Test
    void testCycleDetection() {
        String json = """
            {
              "topics": [
                { "topicId": "A", "title": "A", "prerequisites": ["C"] },
                { "topicId": "B", "title": "B", "prerequisites": ["A"] },
                { "topicId": "C", "title": "C", "prerequisites": ["B"] }
              ]
            }
            """;
        Exception e = assertThrows(IllegalArgumentException.class, () -> CurriculumLoader.loadCurriculum(createStream(json)));
        assertTrue(e.getMessage().contains("Curriculum contains a prerequisite cycle"));
    }

    @Test
    void testMalformedJson() {
        String json = "{ malformed }";
        Exception e = assertThrows(IllegalArgumentException.class, () -> CurriculumLoader.loadCurriculum(createStream(json)));
        assertTrue(e.getMessage().contains("Malformed"));
    }
}
