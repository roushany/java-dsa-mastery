package com.javadsamastery.engine;

import com.javadsamastery.domain.evidence.EvaluationResult;
import com.javadsamastery.domain.evidence.EvidenceRecord;
import com.javadsamastery.domain.learner.LearnerProfile;
import com.javadsamastery.domain.lesson.QuestionType;
import com.javadsamastery.domain.problem.ProblemDifficulty;
import com.javadsamastery.domain.topic.TopicMastery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MasteryEngineTest {
    
    private MasteryEngine engine;
    private LearnerProfile profile;
    private final String TOPIC = "linear-search";

    @BeforeEach
    void setUp() {
        engine = new MasteryEngine();
        profile = new LearnerProfile("user1");
    }

    @Test
    void test1_CorrectAnswerWithHeavyHintsDoesNotEqualMastery() {
        List<EvidenceRecord> history = List.of(
            createRecord(EvaluationResult.PASS, QuestionType.CONCEPT, 3, false),
            createRecord(EvaluationResult.PASS, QuestionType.CONCEPT, 2, false)
        );
        engine.evaluateMastery(profile, TOPIC, history);
        assertEquals(TopicMastery.NOVICE, profile.getMastery(TOPIC));
    }

    @Test
    void test2_IndependentSolutionIncreasesMastery() {
        List<EvidenceRecord> history = List.of(
            createRecord(EvaluationResult.PASS, QuestionType.CONCEPT, 0, false),
            createRecord(EvaluationResult.PASS, QuestionType.CONCEPT, 0, false)
        );
        engine.evaluateMastery(profile, TOPIC, history);
        assertEquals(TopicMastery.FAMILIAR, profile.getMastery(TOPIC));
    }

    @Test
    void test3_SuccessfulTransferAndRetrievalIncreasesMastery() {
        List<EvidenceRecord> history = new ArrayList<>();
        // Get to PROFICIENT
        history.add(createRecord(EvaluationResult.PASS, QuestionType.CONCEPT, 0, false));
        history.add(createRecord(EvaluationResult.PASS, QuestionType.CONCEPT, 0, false));
        history.add(createRecord(EvaluationResult.PASS, QuestionType.IMPLEMENTATION, 0, false));
        history.add(createRecord(EvaluationResult.PASS, QuestionType.IMPLEMENTATION, 0, false));
        
        // Transfer and Retrieval
        history.add(createRecord(EvaluationResult.PASS, QuestionType.TRANSFER, 0, false));
        history.add(createRecord(EvaluationResult.PASS, QuestionType.IMPLEMENTATION, 0, true)); // Retrieval
        
        engine.evaluateMastery(profile, TOPIC, history);
        assertEquals(TopicMastery.MASTERED, profile.getMastery(TOPIC));
    }

    @Test
    void test4_RepeatedMisconceptionReducesConfidence() {
        // First get to PROFICIENT
        List<EvidenceRecord> history = new ArrayList<>();
        history.add(createRecord(EvaluationResult.PASS, QuestionType.CONCEPT, 0, false));
        history.add(createRecord(EvaluationResult.PASS, QuestionType.CONCEPT, 0, false));
        history.add(createRecord(EvaluationResult.PASS, QuestionType.IMPLEMENTATION, 0, false));
        history.add(createRecord(EvaluationResult.PASS, QuestionType.IMPLEMENTATION, 0, false));
        
        engine.evaluateMastery(profile, TOPIC, history);
        assertEquals(TopicMastery.PROFICIENT, profile.getMastery(TOPIC));
        
        // 2 consecutive fails drops it back to FAMILIAR
        history.add(createRecord(EvaluationResult.FAIL, QuestionType.IMPLEMENTATION, 0, false));
        history.add(createRecord(EvaluationResult.FAIL, QuestionType.IMPLEMENTATION, 0, false));
        
        engine.evaluateMastery(profile, TOPIC, history);
        assertEquals(TopicMastery.FAMILIAR, profile.getMastery(TOPIC));
    }

    @Test
    void test5_RetrievalFailureTriggersRemediation() {
        List<EvidenceRecord> history = new ArrayList<>();
        history.add(createRecord(EvaluationResult.PASS, QuestionType.CONCEPT, 0, false));
        history.add(createRecord(EvaluationResult.PASS, QuestionType.CONCEPT, 0, false));
        history.add(createRecord(EvaluationResult.PASS, QuestionType.IMPLEMENTATION, 0, false));
        history.add(createRecord(EvaluationResult.PASS, QuestionType.IMPLEMENTATION, 0, false));
        
        // Single retrieval fail triggers drop to FAMILIAR
        history.add(createRecord(EvaluationResult.FAIL, QuestionType.IMPLEMENTATION, 0, true));
        
        engine.evaluateMastery(profile, TOPIC, history);
        assertEquals(TopicMastery.FAMILIAR, profile.getMastery(TOPIC));
    }

    @Test
    void test6_MasteryCannotExceedDefinedLimits() {
        List<EvidenceRecord> history = new ArrayList<>();
        // Bombard with passes
        for(int i = 0; i < 20; i++) {
            history.add(createRecord(EvaluationResult.PASS, QuestionType.CONCEPT, 0, false));
            history.add(createRecord(EvaluationResult.PASS, QuestionType.IMPLEMENTATION, 0, false));
            history.add(createRecord(EvaluationResult.PASS, QuestionType.TRANSFER, 0, false));
            history.add(createRecord(EvaluationResult.PASS, QuestionType.IMPLEMENTATION, 0, true));
        }
        engine.evaluateMastery(profile, TOPIC, history);
        assertEquals(TopicMastery.MASTERED, profile.getMastery(TOPIC)); // Capped at MASTERED
    }
    
    @Test
    void test7_EvidenceRemainsAuditable() {
        List<EvidenceRecord> history = new ArrayList<>();
        history.add(createRecord(EvaluationResult.PASS, QuestionType.CONCEPT, 0, false));
        engine.evaluateMastery(profile, TOPIC, history);
        
        // Profile doesn't destroy history
        assertEquals(1, history.size());
    }

    private EvidenceRecord createRecord(EvaluationResult result, QuestionType qt, int hintCount, boolean isRetrieval) {
        return new EvidenceRecord("user1", TOPIC, "p1", ProblemDifficulty.EASY, qt, hintCount, isRetrieval, result, null, Instant.now());
    }
}
