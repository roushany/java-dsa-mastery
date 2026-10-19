package com.javadsamastery.session;

import com.javadsamastery.ai.dto.AIAssessment;
import com.javadsamastery.domain.evidence.AttemptContext;
import com.javadsamastery.domain.evidence.EvaluationResult;
import com.javadsamastery.domain.evidence.EvidencePolicy;
import com.javadsamastery.domain.evidence.EvidenceRecord;
import com.javadsamastery.domain.lesson.LearningState;
import com.javadsamastery.domain.lesson.QuestionType;
import com.javadsamastery.domain.problem.ProblemDifficulty;
import com.javadsamastery.domain.learner.LearnerProfile;
import com.javadsamastery.domain.topic.TopicMastery;
import com.javadsamastery.engine.MasteryEngine;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HashingEvaluationTest {

    private AttemptContext createContext(LearningState state) {
        return new AttemptContext(
            "attempt-123",
            "hashing",
            "h-q1",
            state,
            QuestionType.CONCEPT,
            ProblemDifficulty.MEDIUM,
            "learner-eval",
            List.of("COLLISION_CONFUSION", "EQUALITY_CONFUSION", "MUTATION_ERROR")
        );
    }

    @Test
    void testCorrectAnswerAccepted() {
        AttemptContext ctx = createContext(LearningState.CONCEPT_ASSESSMENT);
        AIAssessment assessment = new AIAssessment("attempt-123", "hashing", "h-q1", EvaluationResult.PASS, "Good", null);

        EvidenceRecord rec = EvidencePolicy.authorize(assessment, ctx, 0, true, Set.of("COLLISION_CONFUSION"));

        assertEquals(EvaluationResult.PASS, rec.result());
    }

    @Test
    void testIncorrectAnswerRejected() {
        AttemptContext ctx = createContext(LearningState.CONCEPT_ASSESSMENT);
        AIAssessment assessment = new AIAssessment("attempt-123", "hashing", "h-q1", EvaluationResult.FAIL, "Wrong", "COLLISION_CONFUSION");

        EvidenceRecord rec = EvidencePolicy.authorize(assessment, ctx, 0, true, Set.of("COLLISION_CONFUSION"));

        assertEquals(EvaluationResult.FAIL, rec.result());
        assertEquals("COLLISION_CONFUSION", rec.misconceptionCategory());
    }

    @Test
    void testUnrelatedAnswerInvalidResponseNoPenalty() {
        AttemptContext ctx = createContext(LearningState.CONCEPT_ASSESSMENT);
        AIAssessment assessment = new AIAssessment("attempt-123", "hashing", "h-q1", EvaluationResult.INVALID_RESPONSE, "Unrelated", null);

        EvidenceRecord rec = EvidencePolicy.authorize(assessment, ctx, 0, true, Set.of("COLLISION_CONFUSION"));

        assertEquals(EvaluationResult.INVALID_RESPONSE, rec.result());

        // Ensure INVALID_RESPONSE does not cause mastery penalty
        LearnerProfile profile = new LearnerProfile("learner-eval");
        MasteryEngine engine = new MasteryEngine();
        profile.getTopicState("hashing").evaluateFromHistory(List.of(
            // Pre-existing success
            new EvidenceRecord("learner-eval", "hashing", "h-q0", ProblemDifficulty.MEDIUM, QuestionType.CONCEPT, 0, false, EvaluationResult.PASS, null, Instant.now().minusSeconds(10)),
            new EvidenceRecord("learner-eval", "hashing", "h-q0", ProblemDifficulty.MEDIUM, QuestionType.CONCEPT, 0, false, EvaluationResult.PASS, null, Instant.now().minusSeconds(5)),
            // The invalid response
            rec
        ));

        assertEquals(0, profile.getTopicState("hashing").getConsecutiveFailures(), "INVALID_RESPONSE should not count as a failure");
        assertEquals(TopicMastery.FAMILIAR, profile.getTopicState("hashing").getMastery(), "Mastery should be unaffected");
    }

    @Test
    void testUnauthorizedMisconceptionRejected() {
        AttemptContext ctx = createContext(LearningState.CONCEPT_ASSESSMENT);
        AIAssessment assessment = new AIAssessment("attempt-123", "hashing", "h-q1", EvaluationResult.FAIL, "Wrong", "MADE_UP_MISCONCEPTION");

        EvidenceRecord rec = EvidencePolicy.authorize(assessment, ctx, 0, true, Set.of("COLLISION_CONFUSION", "EQUALITY_CONFUSION"));

        assertEquals(EvaluationResult.FAIL, rec.result());
        assertNull(rec.misconceptionCategory(), "Unauthorized misconception should be silently stripped");
    }

    @Test
    void testRetrievalEvidenceBoundCorrectly() {
        AttemptContext ctx = createContext(LearningState.RETRIEVAL);
        AIAssessment assessment = new AIAssessment("attempt-123", "hashing", "h-q1", EvaluationResult.PASS, "Good", null);

        EvidenceRecord rec = EvidencePolicy.authorize(assessment, ctx, 0, true, Set.of());

        assertTrue(rec.isRetrievalPractice(), "Should be flagged as retrieval practice");
    }

    @Test
    void testDuplicateOrStaleEvidenceRejected() {
        AttemptContext ctx = createContext(LearningState.CONCEPT_ASSESSMENT);
        AIAssessment staleAssessment = new AIAssessment("attempt-999", "hashing", "h-q1", EvaluationResult.PASS, "Good", null);

        EvidenceRecord rec = EvidencePolicy.authorize(staleAssessment, ctx, 0, true, Set.of());

        assertEquals(EvaluationResult.INVALID_RESPONSE, rec.result(), "Stale attempt ID should be rejected as INVALID_RESPONSE");
    }

    @Test
    void testHintAssistedAnswerNotIndependent() {
        AttemptContext ctx = createContext(LearningState.IMPLEMENTATION);
        AIAssessment assessment = new AIAssessment("attempt-123", "hashing", "h-q1", EvaluationResult.PASS, "Good", null);

        EvidenceRecord rec = EvidencePolicy.authorize(assessment, ctx, 1, true, Set.of()); // hintCount = 1

        assertEquals(EvaluationResult.PASS, rec.result());

        LearnerProfile profile = new LearnerProfile("learner-hint");
        MasteryEngine engine = new MasteryEngine();
        engine.evaluateMastery(profile, "hashing", List.of(rec));

        assertFalse(profile.getTopicState("hashing").isImplementationStrong(), "Hint-assisted pass does not prove implementation strength");
    }

    @Test
    void testMisconceptionRemediationFlow() {
        LearnerProfile profile = new LearnerProfile("learner-remediation");
        MasteryEngine engine = new MasteryEngine();

        // 1. Fail with misconception
        EvidenceRecord rec1 = new EvidenceRecord("learner-remediation", "hashing", "h-q1", ProblemDifficulty.MEDIUM, QuestionType.CONCEPT, 0, false, EvaluationResult.FAIL, "COLLISION_CONFUSION", Instant.now().minusSeconds(10));
        engine.evaluateMastery(profile, "hashing", List.of(rec1));

        assertTrue(profile.getTopicState("hashing").getActiveMisconceptions().contains("COLLISION_CONFUSION"));
        assertEquals(1, profile.getTopicState("hashing").getConsecutiveFailures());

        // 2. Subsequent PASS clears misconception
        EvidenceRecord rec2 = new EvidenceRecord("learner-remediation", "hashing", "h-q2", ProblemDifficulty.MEDIUM, QuestionType.CONCEPT, 0, false, EvaluationResult.PASS, null, Instant.now());
        engine.evaluateMastery(profile, "hashing", List.of(rec1, rec2));

        assertFalse(profile.getTopicState("hashing").getActiveMisconceptions().contains("COLLISION_CONFUSION"));
        assertEquals(0, profile.getTopicState("hashing").getConsecutiveFailures());
    }
}
