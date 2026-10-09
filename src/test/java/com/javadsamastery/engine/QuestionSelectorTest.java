package com.javadsamastery.engine;

import com.javadsamastery.domain.content.QuestionBank;
import com.javadsamastery.domain.content.QuestionDefinition;
import com.javadsamastery.domain.evidence.EvaluationResult;
import com.javadsamastery.domain.evidence.EvidenceRecord;
import com.javadsamastery.domain.learner.TopicState;
import com.javadsamastery.domain.lesson.LearningState;
import com.javadsamastery.domain.lesson.QuestionType;
import com.javadsamastery.domain.problem.ProblemDifficulty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuestionSelectorTest {
    
    private QuestionBank bank;
    private QuestionSelector selector;

    @BeforeEach
    void setUp() {
        bank = new QuestionBank();
        String json = """
            [
              {
                "questionId": "q1",
                "topicId": "topic-1",
                "targetStage": "IMPLEMENTATION",
                "difficulty": "MEDIUM",
                "content": "Implement X.",
                "expectedEvidence": "Code.",
                "targetsMisconception": null,
                "isRemediation": false,
                "isTransfer": false,
                "isRetrieval": false
              },
              {
                "questionId": "q2",
                "topicId": "topic-1",
                "targetStage": "IMPLEMENTATION",
                "difficulty": "EASY",
                "content": "Implement X (Remediation).",
                "expectedEvidence": "Code.",
                "targetsMisconception": "off-by-one",
                "isRemediation": true,
                "isTransfer": false,
                "isRetrieval": false
              },
              {
                "questionId": "q3",
                "topicId": "topic-1",
                "targetStage": "IMPLEMENTATION",
                "difficulty": "HARD",
                "content": "Implement X (Hard).",
                "expectedEvidence": "Code.",
                "targetsMisconception": null,
                "isRemediation": false,
                "isTransfer": false,
                "isRetrieval": false
              }
            ]
            """;
        bank.loadFromJsonStream(new ByteArrayInputStream(json.getBytes()));
        selector = new QuestionSelector(bank);
    }

    @Test
    void testStandardProgression() {
        TopicState state = new TopicState("topic-1");
        
        QuestionSelectionResult result = selector.selectNextQuestion(state, LearningState.IMPLEMENTATION);
        
        assertEquals("q1", result.selectedQuestion().questionId());
        assertEquals("Selected standard progression at difficulty: MEDIUM", result.explanation());
    }

    @Test
    void testMisconceptionTargeting() {
        TopicState state = new TopicState("topic-1");
        // Inject history with a misconception
        state.evaluateFromHistory(List.of(
            new EvidenceRecord("learner-1", "topic-1", "q0", ProblemDifficulty.MEDIUM, QuestionType.IMPLEMENTATION, 0, false, EvaluationResult.FAIL, "off-by-one", Instant.now())
        ));
        
        QuestionSelectionResult result = selector.selectNextQuestion(state, LearningState.IMPLEMENTATION);
        
        assertEquals("q2", result.selectedQuestion().questionId());
        assertTrue(result.explanation().contains("address active misconception"));
    }

    @Test
    void testDifficultyIncreaseOnStrongPerformance() {
        TopicState state = new TopicState("topic-1");
        state.evaluateFromHistory(List.of(
            new EvidenceRecord("learner-1", "topic-1", "q0", ProblemDifficulty.MEDIUM, QuestionType.IMPLEMENTATION, 0, false, EvaluationResult.PASS, null, Instant.now())
        ));
        
        // Learner passed implementation cleanly, so implementationStrong = true
        assertTrue(state.isImplementationStrong());
        
        QuestionSelectionResult result = selector.selectNextQuestion(state, LearningState.IMPLEMENTATION);
        
        assertEquals("q3", result.selectedQuestion().questionId());
        assertEquals("Selected standard progression at difficulty: HARD", result.explanation());
    }
}
