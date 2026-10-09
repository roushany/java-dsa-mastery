package com.javadsamastery.domain.problem;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ProblemTest {
    @Test
    void testValidProblem() {
        Problem problem = new Problem("p1", "Two Sum", ProblemDifficulty.EASY, Set.of("arrays", "hashing"));
        assertEquals("p1", problem.id());
        assertTrue(problem.topicIds().contains("arrays"));
    }

    @Test
    void testImmutability() {
        Set<String> topics = new java.util.HashSet<>();
        topics.add("arrays");
        Problem problem = new Problem("p1", "Title", ProblemDifficulty.EASY, topics);
        topics.add("trees");
        
        // Internal set should not be affected by external modifications
        assertFalse(problem.topicIds().contains("trees"));
        
        // Returned set should be unmodifiable
        assertThrows(UnsupportedOperationException.class, () -> problem.topicIds().add("graphs"));
    }
}
