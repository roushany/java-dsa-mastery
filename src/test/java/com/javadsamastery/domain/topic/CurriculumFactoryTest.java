package com.javadsamastery.domain.topic;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CurriculumFactoryTest {

    @Test
    void testStandardCurriculumProgression() {
        CurriculumGraph graph = CurriculumFactory.createStandardCurriculum();
        
        // Verify sequential structure
        assertTrue(graph.getPrerequisites("binary-search").contains("linear-search"));
        assertTrue(graph.getPrerequisites("dynamic-programming").contains("greedy"));
        
        // Cannot access two-pointers without mastering binary search
        assertFalse(graph.canAccess("two-pointers", Set.of("foundations", "arrays", "strings", "linear-search")));
        
        // Can access two-pointers when binary-search is mastered
        assertTrue(graph.canAccess("two-pointers", Set.of("foundations", "arrays", "strings", "linear-search", "binary-search")));
    }
}
