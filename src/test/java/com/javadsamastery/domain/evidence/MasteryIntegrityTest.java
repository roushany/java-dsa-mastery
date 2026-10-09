package com.javadsamastery.domain.evidence;

import com.javadsamastery.ai.dto.AIAssessment;
import com.javadsamastery.domain.learner.LearnerProfile;
import com.javadsamastery.domain.learner.TopicState;
import com.javadsamastery.domain.lesson.LearningState;
import com.javadsamastery.domain.lesson.QuestionType;
import com.javadsamastery.domain.problem.ProblemDifficulty;
import com.javadsamastery.domain.topic.TopicMastery;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MasteryIntegrityTest {

    @Test
    void testMasteryIsPurelyDerived() {
        // This proves TopicState correctly derives mastery from history and doesn't rely on setters
        TopicState state = new TopicState("linear-search");
        assertEquals(TopicMastery.NOVICE, state.getMastery());
        
        // Pass 2 concepts -> FAMILIAR
        state.evaluateFromHistory(List.of(
            new EvidenceRecord("u1", "linear-search", "q1", ProblemDifficulty.EASY, QuestionType.CONCEPT, 0, false, EvaluationResult.PASS, null, Instant.now()),
            new EvidenceRecord("u1", "linear-search", "q2", ProblemDifficulty.EASY, QuestionType.CONCEPT, 0, false, EvaluationResult.PASS, null, Instant.now())
        ));
        assertEquals(TopicMastery.FAMILIAR, state.getMastery());
    }
    
    @Test
    void testReconstructionEquivalence() {
        // Proves that history -> Profile A == history -> Profile B
        List<EvidenceRecord> history = List.of(
            new EvidenceRecord("u1", "linear-search", "q1", ProblemDifficulty.EASY, QuestionType.CONCEPT, 0, false, EvaluationResult.PASS, null, Instant.now()),
            new EvidenceRecord("u1", "linear-search", "q2", ProblemDifficulty.EASY, QuestionType.CONCEPT, 0, false, EvaluationResult.PASS, null, Instant.now()),
            new EvidenceRecord("u1", "linear-search", "q3", ProblemDifficulty.MEDIUM, QuestionType.IMPLEMENTATION, 0, false, EvaluationResult.PASS, null, Instant.now()),
            new EvidenceRecord("u1", "linear-search", "q4", ProblemDifficulty.MEDIUM, QuestionType.IMPLEMENTATION, 0, false, EvaluationResult.PASS, null, Instant.now())
        );
        
        TopicState stateA = new TopicState("linear-search");
        stateA.evaluateFromHistory(history);
        
        TopicState stateB = new TopicState("linear-search");
        stateB.evaluateFromHistory(history);
        
        assertEquals(stateA.getMastery(), stateB.getMastery());
        assertEquals(TopicMastery.PROFICIENT, stateA.getMastery());
        assertEquals(stateA.isImplementationStrong(), stateB.isImplementationStrong());
        assertTrue(stateA.isImplementationStrong());
    }

    @Test
    void testOrderingMatters() {
        // Order A: Pass -> Fail -> Fail -> Fail
        List<EvidenceRecord> historyFailLast = List.of(
            new EvidenceRecord("u1", "linear-search", "q1", ProblemDifficulty.MEDIUM, QuestionType.IMPLEMENTATION, 0, false, EvaluationResult.PASS, null, Instant.now()),
            new EvidenceRecord("u1", "linear-search", "q2", ProblemDifficulty.MEDIUM, QuestionType.IMPLEMENTATION, 0, false, EvaluationResult.PASS, null, Instant.now()),
            new EvidenceRecord("u1", "linear-search", "q3", ProblemDifficulty.MEDIUM, QuestionType.IMPLEMENTATION, 0, false, EvaluationResult.FAIL, null, Instant.now()),
            new EvidenceRecord("u1", "linear-search", "q4", ProblemDifficulty.MEDIUM, QuestionType.IMPLEMENTATION, 0, false, EvaluationResult.FAIL, null, Instant.now())
        );
        TopicState stateA = new TopicState("linear-search");
        // Start as familiar by injecting concept passes? No, let's just observe consecutiveFailures
        stateA.evaluateFromHistory(historyFailLast);
        
        // Order B: Fail -> Fail -> Fail -> Pass -> Pass
        List<EvidenceRecord> historyPassLast = List.of(
            new EvidenceRecord("u1", "linear-search", "q3", ProblemDifficulty.MEDIUM, QuestionType.IMPLEMENTATION, 0, false, EvaluationResult.FAIL, null, Instant.now()),
            new EvidenceRecord("u1", "linear-search", "q4", ProblemDifficulty.MEDIUM, QuestionType.IMPLEMENTATION, 0, false, EvaluationResult.FAIL, null, Instant.now()),
            new EvidenceRecord("u1", "linear-search", "q1", ProblemDifficulty.MEDIUM, QuestionType.IMPLEMENTATION, 0, false, EvaluationResult.PASS, null, Instant.now()),
            new EvidenceRecord("u1", "linear-search", "q2", ProblemDifficulty.MEDIUM, QuestionType.IMPLEMENTATION, 0, false, EvaluationResult.PASS, null, Instant.now())
        );
        TopicState stateB = new TopicState("linear-search");
        stateB.evaluateFromHistory(historyPassLast);
        
        assertNotEquals(stateA.getConsecutiveFailures(), stateB.getConsecutiveFailures());
        assertEquals(2, stateA.getConsecutiveFailures());
        assertEquals(0, stateB.getConsecutiveFailures());
    }
    
    @Test
    void testMisconceptionCanBeCleared() {
        TopicState state = new TopicState("linear-search");
        
        // Step 1: Fail with misconception
        state.evaluateFromHistory(List.of(
            new EvidenceRecord("u1", "linear-search", "q1", ProblemDifficulty.MEDIUM, QuestionType.IMPLEMENTATION, 0, false, EvaluationResult.FAIL, "off-by-one", Instant.now())
        ));
        
        assertTrue(state.getActiveMisconceptions().contains("off-by-one"));
        
        // Step 2: Pass clears the misconception
        state.evaluateFromHistory(List.of(
            new EvidenceRecord("u1", "linear-search", "q1", ProblemDifficulty.MEDIUM, QuestionType.IMPLEMENTATION, 0, false, EvaluationResult.FAIL, "off-by-one", Instant.now()),
            new EvidenceRecord("u1", "linear-search", "q2", ProblemDifficulty.MEDIUM, QuestionType.IMPLEMENTATION, 0, false, EvaluationResult.PASS, null, Instant.now())
        ));
        
        assertFalse(state.getActiveMisconceptions().contains("off-by-one"));
    }
    
    @Test
    void testDuplicateEvidenceIdempotency() {
        // If we add attemptId to EvidenceRecord, we can enforce uniqueness.
        // For now, if the same history is evaluated, it should be equivalent, but if the same record is sent twice, 
        // the engine should probably filter by attemptId.
        // We will implement `attemptId` filtering in TopicState.evaluateFromHistory.
    }
}
