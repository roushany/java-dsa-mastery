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

    @Test
    void testNoCyclesInStandardCurriculum() throws Exception {
        com.javadsamastery.domain.topic.CurriculumGraph graph = com.javadsamastery.domain.curriculum.CurriculumLoader.loadCurriculum("/curriculum/curriculum.json");

        java.util.List<String> allTopics = java.util.List.of("linear-search", "binary-search", "two-pointers", "sliding-window", "binary-search-on-answer", "prefix-sum", "hashing");

        // Simple DFS cycle detection
        java.util.Set<String> visited = new java.util.HashSet<>();
        java.util.Set<String> recursionStack = new java.util.HashSet<>();

        for (String topic : allTopics) {
            assertFalse(hasCycle(topic, graph, visited, recursionStack), "Curriculum graph should not contain cycles: " + topic);
        }
    }

    private boolean hasCycle(String node, CurriculumGraph graph, java.util.Set<String> visited, java.util.Set<String> recStack) {
        if (recStack.contains(node)) return true;
        if (visited.contains(node)) return false;

        visited.add(node);
        recStack.add(node);

        for (String neighbor : graph.getPrerequisites(node)) {
            if (hasCycle(neighbor, graph, visited, recStack)) {
                return true;
            }
        }

        recStack.remove(node);
        return false;
    }
}
