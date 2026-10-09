package com.javadsamastery.session;

import com.javadsamastery.domain.content.QuestionBank;
import com.javadsamastery.domain.content.QuestionDefinition;
import com.javadsamastery.domain.evidence.EvaluationResult;
import com.javadsamastery.domain.evidence.EvidenceRecord;
import com.javadsamastery.domain.learner.TopicState;
import com.javadsamastery.domain.lesson.LearningState;
import com.javadsamastery.domain.lesson.QuestionType;
import com.javadsamastery.domain.problem.ProblemDifficulty;
import com.javadsamastery.engine.QuestionSelectionResult;
import com.javadsamastery.engine.QuestionSelector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class AdaptiveLearningSimulationTest {

    private QuestionBank questionBank;
    private QuestionSelector selector;

    @BeforeEach
    void setUp() {
        questionBank = new QuestionBank();
        String json = """
        [
          {"questionId": "q1", "topicId": "tp", "targetStage": "CONCEPT_ASSESSMENT", "difficulty": "EASY", "isRemediation": false, "targetsMisconception": null, "allowedMisconceptions": ["OFF_BY_ONE"], "content": "c", "expectedEvidence": "e"},
          {"questionId": "q2", "topicId": "tp", "targetStage": "CONCEPT_ASSESSMENT", "difficulty": "MEDIUM", "isRemediation": false, "targetsMisconception": null, "content": "c", "expectedEvidence": "e"},
          {"questionId": "q3", "topicId": "tp", "targetStage": "DERIVE", "difficulty": "MEDIUM", "isRemediation": false, "targetsMisconception": null, "content": "c", "expectedEvidence": "e"},
          {"questionId": "q4", "topicId": "tp", "targetStage": "DERIVE", "difficulty": "HARD", "isRemediation": false, "targetsMisconception": null, "content": "c", "expectedEvidence": "e"},
          {"questionId": "qRemediation", "topicId": "tp", "targetStage": "DERIVE", "difficulty": "EASY", "isRemediation": true, "targetsMisconception": null, "content": "c", "expectedEvidence": "e"},
          {"questionId": "q5", "topicId": "tp", "targetStage": "IMPLEMENTATION", "difficulty": "MEDIUM", "isRemediation": false, "targetsMisconception": null, "allowedMisconceptions": ["OFF_BY_ONE"], "content": "c", "expectedEvidence": "e"},
          {"questionId": "qImplHard", "topicId": "tp", "targetStage": "IMPLEMENTATION", "difficulty": "HARD", "isRemediation": false, "targetsMisconception": null, "content": "c", "expectedEvidence": "e"},
          {"questionId": "qImplEasy", "topicId": "tp", "targetStage": "IMPLEMENTATION", "difficulty": "EASY", "isRemediation": true, "targetsMisconception": null, "content": "c", "expectedEvidence": "e"},
          {"questionId": "q6", "topicId": "tp", "targetStage": "EXPLANATION", "difficulty": "MEDIUM", "isRemediation": false, "targetsMisconception": null, "content": "c", "expectedEvidence": "e"},
          {"questionId": "q7", "topicId": "tp", "targetStage": "TRANSFER", "difficulty": "MEDIUM", "isRemediation": false, "targetsMisconception": null, "content": "c", "expectedEvidence": "e"},
          {"questionId": "q8", "topicId": "tp", "targetStage": "TRANSFER", "difficulty": "HARD", "isRemediation": false, "targetsMisconception": null, "content": "c", "expectedEvidence": "e"},
          {"questionId": "qMisc1", "topicId": "tp", "targetStage": "IMPLEMENTATION", "difficulty": "MEDIUM", "isRemediation": false, "targetsMisconception": "OFF_BY_ONE", "content": "c", "expectedEvidence": "e"},
          {"questionId": "qMisc2", "topicId": "tp", "targetStage": "CONCEPT_ASSESSMENT", "difficulty": "EASY", "isRemediation": false, "targetsMisconception": "WRONG_POINTER", "content": "c", "expectedEvidence": "e"},
          {"questionId": "qRet", "topicId": "tp", "targetStage": "TRANSFER", "difficulty": "HARD", "isRemediation": false, "targetsMisconception": null, "isRetrieval": true, "content": "c", "expectedEvidence": "e"}
        ]
        """;
        questionBank.loadFromJsonStream(new ByteArrayInputStream(json.getBytes()));
        selector = new QuestionSelector(questionBank);
    }

    private EvidenceRecord pass(String topic, QuestionType qt, boolean independent) {
        return new EvidenceRecord("u1", topic, "prob", ProblemDifficulty.MEDIUM, qt, independent ? 0 : 1, false, EvaluationResult.PASS, null, Instant.now());
    }

    private EvidenceRecord fail(String topic, QuestionType qt, String misconception) {
        return new EvidenceRecord("u1", topic, "prob", ProblemDifficulty.MEDIUM, qt, 0, false, EvaluationResult.FAIL, misconception, Instant.now());
    }

    @Test
    void testScenarioA_StrongLearnerPrefersHarderQuestions() {
        TopicState state = new TopicState("tp");
        state.evaluateFromHistory(List.of(
            pass("tp", QuestionType.CONCEPT, true),
            pass("tp", QuestionType.DERIVATION, true),
            pass("tp", QuestionType.IMPLEMENTATION, true)
        ));

        // When asking for IMPL question, should prefer HARD due to strong implementation
        QuestionSelectionResult resImpl = selector.selectNextQuestion(state, LearningState.IMPLEMENTATION);
        assertEquals("qImplHard", resImpl.selectedQuestion().questionId());

        // When asking for DERIVE question, should prefer HARD due to strong derivation
        QuestionSelectionResult resDerive = selector.selectNextQuestion(state, LearningState.DERIVE);
        assertEquals("q4", resDerive.selectedQuestion().questionId());
    }

    @Test
    void testScenarioB_DerivationWeakness() {
        TopicState state = new TopicState("tp");
        state.evaluateFromHistory(List.of(
            pass("tp", QuestionType.CONCEPT, true),
            fail("tp", QuestionType.DERIVATION, null),
            fail("tp", QuestionType.DERIVATION, null)
        ));

        // Weak derivation -> select easy/remediation
        QuestionSelectionResult res = selector.selectNextQuestion(state, LearningState.DERIVE);
        assertEquals("qRemediation", res.selectedQuestion().questionId());
    }

    @Test
    void testScenarioC_ImplementationFailure() {
        TopicState state = new TopicState("tp");
        state.evaluateFromHistory(List.of(
            pass("tp", QuestionType.CONCEPT, true),
            pass("tp", QuestionType.DERIVATION, true),
            fail("tp", QuestionType.IMPLEMENTATION, null),
            fail("tp", QuestionType.IMPLEMENTATION, null)
        ));

        QuestionSelectionResult res = selector.selectNextQuestion(state, LearningState.IMPLEMENTATION);
        assertEquals("qImplEasy", res.selectedQuestion().questionId(), "Should prioritize implementation remediation");
    }

    @Test
    void testScenarioD_ExplanationWeakness() {
        TopicState state = new TopicState("tp");
        state.evaluateFromHistory(List.of(
            pass("tp", QuestionType.CONCEPT, true),
            pass("tp", QuestionType.DERIVATION, true),
            pass("tp", QuestionType.IMPLEMENTATION, true),
            fail("tp", QuestionType.CORRECTNESS, null) // Correctness maps to EXPLANATION stage
        ));

        // Learner asks for EXPLANATION objective
        QuestionSelectionResult res = selector.selectNextQuestion(state, LearningState.EXPLANATION);
        assertEquals("q6", res.selectedQuestion().questionId());
        
        // Ensure implementation is still strong
        assertTrue(state.isImplementationStrong());
    }

    @Test
    void testScenarioE_Misconception() {
        TopicState state = new TopicState("tp");
        state.evaluateFromHistory(List.of(
            fail("tp", QuestionType.IMPLEMENTATION, "OFF_BY_ONE")
        ));

        assertTrue(state.getActiveMisconceptions().contains("OFF_BY_ONE"));

        QuestionSelectionResult res = selector.selectNextQuestion(state, LearningState.IMPLEMENTATION);
        assertEquals("qMisc1", res.selectedQuestion().questionId(), "Should target OFF_BY_ONE misconception");
    }

    @Test
    void testScenarioF_MisconceptionResolution() {
        TopicState state = new TopicState("tp");
        
        // Fail with misconception -> PASS (clears it)
        state.evaluateFromHistory(List.of(
            fail("tp", QuestionType.IMPLEMENTATION, "OFF_BY_ONE"),
            pass("tp", QuestionType.IMPLEMENTATION, true)
        ));

        assertFalse(state.getActiveMisconceptions().contains("OFF_BY_ONE"));

        // Now normal objective question is selected, not the misconception one
        QuestionSelectionResult res = selector.selectNextQuestion(state, LearningState.IMPLEMENTATION);
        assertNotEquals("qMisc1", res.selectedQuestion().questionId());
    }

    @Test
    void testScenarioG_HintDependency() {
        TopicState stateIndep = new TopicState("tp");
        stateIndep.evaluateFromHistory(List.of(
            pass("tp", QuestionType.IMPLEMENTATION, true)
        ));
        
        TopicState stateHinted = new TopicState("tp");
        stateHinted.evaluateFromHistory(List.of(
            pass("tp", QuestionType.IMPLEMENTATION, false) // hint > 0
        ));

        assertTrue(stateIndep.isImplementationStrong());
        assertFalse(stateHinted.isImplementationStrong());
    }

    @Test
    void testScenarioH_SolutionWalkthrough() {
        TopicState state = new TopicState("tp");
        // We simulate a walkthrough as a fail or a special record, but let's just say they failed multiple times, got walkthrough, then passed independently.
        state.evaluateFromHistory(List.of(
            fail("tp", QuestionType.IMPLEMENTATION, null),
            fail("tp", QuestionType.IMPLEMENTATION, null),
            fail("tp", QuestionType.IMPLEMENTATION, null), // Imagine this led to Walkthrough
            pass("tp", QuestionType.IMPLEMENTATION, true)  // Later independent pass
        ));

        assertTrue(state.isImplementationStrong(), "Independent success later still counts");
    }

    @Test
    void testScenarioI_TransferFailure() {
        TopicState state = new TopicState("tp");
        state.evaluateFromHistory(List.of(
            pass("tp", QuestionType.IMPLEMENTATION, true),
            fail("tp", QuestionType.TRANSFER, null)
        ));

        assertFalse(state.isTransferStrong());
        QuestionSelectionResult res = selector.selectNextQuestion(state, LearningState.TRANSFER);
        assertEquals("q7", res.selectedQuestion().questionId(), "Picks medium difficulty transfer because not strong");
    }

    @Test
    void testScenarioJ_Retrieval() {
        TopicState state = new TopicState("tp");
        EvidenceRecord retrievalRecord = new EvidenceRecord("u1", "tp", "qRet", ProblemDifficulty.HARD, QuestionType.TRANSFER, 0, true, EvaluationResult.PASS, null, Instant.now());
        state.evaluateFromHistory(List.of(retrievalRecord));
        
        // Mastered criteria in TopicState requires independentConceptPasses, independentImplementationPasses, etc.
        // We just verify it accepts retrieval correctly.
        assertTrue(state.isTransferStrong()); // A retrieval pass in transfer type counts as transfer pass
    }

    @Test
    void testCrossLearnerDifferentiation() {
        // Learner A: Strong Derive, Weak Impl
        TopicState stateA = new TopicState("tp");
        stateA.evaluateFromHistory(List.of(
            pass("tp", QuestionType.DERIVATION, true),
            fail("tp", QuestionType.IMPLEMENTATION, null)
        ));

        // Learner B: Weak Derive, Strong Impl (at the time of asking derive)
        TopicState stateBDerive = new TopicState("tp");
        stateBDerive.evaluateFromHistory(List.of(
            pass("tp", QuestionType.IMPLEMENTATION, true),
            fail("tp", QuestionType.DERIVATION, null),
            fail("tp", QuestionType.DERIVATION, null)
        ));
        
        // Learner B: Weak Derive, Strong Impl (at the time of asking impl)
        TopicState stateBImpl = new TopicState("tp");
        stateBImpl.evaluateFromHistory(List.of(
            fail("tp", QuestionType.DERIVATION, null),
            fail("tp", QuestionType.DERIVATION, null),
            pass("tp", QuestionType.IMPLEMENTATION, true)
        ));

        // Ask for Derivation
        assertEquals("q4", selector.selectNextQuestion(stateA, LearningState.DERIVE).selectedQuestion().questionId(), "A gets hard derive");
        assertEquals("qRemediation", selector.selectNextQuestion(stateBDerive, LearningState.DERIVE).selectedQuestion().questionId(), "B gets remediation derive");
        
        // Ask for Implementation
        assertEquals("q5", selector.selectNextQuestion(stateA, LearningState.IMPLEMENTATION).selectedQuestion().questionId(), "A gets medium impl (not strong)");
        assertEquals("qImplHard", selector.selectNextQuestion(stateBImpl, LearningState.IMPLEMENTATION).selectedQuestion().questionId(), "B gets hard impl");
    }
    
    @Test
    void testDeterminism() {
        TopicState state1 = new TopicState("tp");
        state1.evaluateFromHistory(List.of(pass("tp", QuestionType.DERIVATION, true)));
        
        TopicState state2 = new TopicState("tp");
        state2.evaluateFromHistory(List.of(pass("tp", QuestionType.DERIVATION, true)));
        
        QuestionSelectionResult res1 = selector.selectNextQuestion(state1, LearningState.DERIVE);
        QuestionSelectionResult res2 = selector.selectNextQuestion(state2, LearningState.DERIVE);
        
        assertEquals(res1.selectedQuestion().questionId(), res2.selectedQuestion().questionId());
    }

    @Test
    void testCompleteFeedbackLoop() {
        // Setup the full loop dependencies
        com.javadsamastery.ai.mock.MockLLMProvider provider = new com.javadsamastery.ai.mock.MockLLMProvider();
        com.javadsamastery.engine.AITutorEngine tutor = new com.javadsamastery.engine.AITutorEngine(
            new com.javadsamastery.ai.service.impl.LLMTutor(provider), 
            new com.javadsamastery.ai.service.impl.LLMQuestionGenerator(provider), 
            new com.javadsamastery.ai.service.impl.LLMAnswerEvaluator(provider)
        );
        com.javadsamastery.domain.learner.LearnerProfile profile = new com.javadsamastery.domain.learner.LearnerProfile("u1");
        com.javadsamastery.engine.MasteryEngine masteryEngine = new com.javadsamastery.engine.MasteryEngine();
        
        LearningSession session = new LearningSession(profile, masteryEngine, selector, tutor);
        session.start();

        // Simulate loop 1
        provider.setCannedResponse(String.class, "{\"evaluation\": \"FAIL\", \"misconceptionCategory\": \"OFF_BY_ONE\"}");
        var q1 = session.getNextQuestion("tp", LearningState.IMPLEMENTATION); // Gets standard IMPL question (q5 medium)
        assertEquals("q5", session.getActiveContextForTest().questionId());
        
        EvidenceRecord ev1 = session.submitAnswer(q1.expectedConcept(), "bad answer", 0, false);
        assertEquals(EvaluationResult.FAIL, ev1.result());
        assertTrue(profile.getTopicState("tp").getActiveMisconceptions().contains("OFF_BY_ONE"));

        // Simulate loop 2 (Misconception active -> gets qMisc1)
        provider.setCannedResponse(String.class, "{\"evaluation\": \"PASS\", \"feedback\": \"Good!\"}");
        var q2 = session.getNextQuestion("tp", LearningState.IMPLEMENTATION); 
        assertEquals("qMisc1", session.getActiveContextForTest().questionId());
        
        EvidenceRecord ev2 = session.submitAnswer(q2.expectedConcept(), "good answer", 0, true);
        assertEquals(EvaluationResult.PASS, ev2.result());
        assertFalse(profile.getTopicState("tp").getActiveMisconceptions().contains("OFF_BY_ONE"), "Misconception should be cleared");
        
        // Ensure evidence was fed back properly
        assertEquals(2, session.getSessionHistory().size());
    }
}
