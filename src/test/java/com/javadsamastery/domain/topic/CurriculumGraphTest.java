package com.javadsamastery.domain.topic;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CurriculumGraphTest {

    @Test
    void testDependencyManagement() {
        CurriculumGraph graph = new CurriculumGraph();
        graph.addDependency("binary-search", "linear-search");
        graph.addDependency("binary-search", "arrays");

        Set<String> prereqs = graph.getPrerequisites("binary-search");
        assertEquals(2, prereqs.size());
        assertTrue(prereqs.contains("linear-search"));
        assertTrue(prereqs.contains("arrays"));
    }

    @Test
    void testCanAccess() {
        CurriculumGraph graph = new CurriculumGraph();
        graph.addDependency("binary-search", "linear-search");

        // Learner has no mastery
        assertFalse(graph.canAccess("binary-search", Set.of()));

        // Learner mastered linear search
        assertTrue(graph.canAccess("binary-search", Set.of("linear-search")));
    }
}
