package com.javadsamastery.integration;

import com.javadsamastery.domain.evidence.*;
import com.javadsamastery.domain.learner.LearnerProfile;
import com.javadsamastery.domain.lesson.QuestionType;
import com.javadsamastery.domain.problem.ProblemDifficulty;
import com.javadsamastery.domain.topic.*;
import com.javadsamastery.engine.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class SystemIntegrationTest {

    /**
     * INTEGRATION & LEARNING-FLOW TEST
     * Verifies the entire learning system interacts correctly:
     * CurriculumGraph + AdaptiveQuestionEngine + MasteryEngine + LearnerProfile.
     */
    @Test
    void testEndToEndLearningSystem() {
        // 1. Curriculum config
        CurriculumGraph graph = CurriculumFactory.createStandardCurriculum();
        LearnerProfile profile = new LearnerProfile("int-test-learner");
        
        // 2. Validate blocked access
        assertFalse(graph.canAccess("binary-search", Set.of()), "Cannot access binary search without prerequisites");
        
        // 3. Question Selector initiates topic
        com.javadsamastery.domain.content.QuestionBank bank = new com.javadsamastery.domain.content.QuestionBank();
        String json = """
            [
              {
                "questionId": "q1",
                "topicId": "linear-search",
                "targetStage": "CONTENT_INTRO",
                "difficulty": "EASY",
                "content": "Intro",
                "expectedEvidence": "Evidence",
                "targetsMisconception": null,
                "isRemediation": false,
                "isTransfer": false,
                "isRetrieval": false
              }
            ]
            """;
        bank.loadFromJsonStream(new java.io.ByteArrayInputStream(json.getBytes()));
        QuestionSelector selector = new QuestionSelector(bank);
        QuestionSelectionResult selRes = selector.selectNextQuestion(profile.getTopicState("linear-search"), com.javadsamastery.domain.lesson.LearningState.CONTENT_INTRO);
        assertEquals("q1", selRes.selectedQuestion().questionId(), "Beginner should start with intuition");
        
        // 4. Mastery Engine processes accumulated evidence
        MasteryEngine masteryEngine = new MasteryEngine();
        List<EvidenceRecord> linearSearchHistory = List.of(
            createRecord("linear-search", QuestionType.CONCEPT, false),
            createRecord("linear-search", QuestionType.CONCEPT, false),
            createRecord("linear-search", QuestionType.IMPLEMENTATION, false),
            createRecord("linear-search", QuestionType.IMPLEMENTATION, false),
            createRecord("linear-search", QuestionType.TRANSFER, false),
            createRecord("linear-search", QuestionType.IMPLEMENTATION, true) // Retrieval
        );
        
        masteryEngine.evaluateMastery(profile, "linear-search", linearSearchHistory);
        
        // 5. Verify mastery unlocking next level
        assertEquals(TopicMastery.MASTERED, profile.getMastery("linear-search"));
        
        Set<String> masteredTopics = Set.of("foundations", "arrays", "strings", "linear-search");
        assertTrue(graph.canAccess("binary-search", masteredTopics), "Should unlock binary search after prerequisites are met");
    }

    private EvidenceRecord createRecord(String topic, QuestionType qt, boolean isRetrieval) {
        return new EvidenceRecord("int-test-learner", topic, "prob", ProblemDifficulty.EASY, qt, 0, isRetrieval, EvaluationResult.PASS, null, Instant.now());
    }
}
