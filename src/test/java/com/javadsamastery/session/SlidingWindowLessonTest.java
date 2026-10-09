package com.javadsamastery.session;

import com.javadsamastery.ai.dto.AIAssessment;
import com.javadsamastery.ai.dto.EvaluationResultDto;
import com.javadsamastery.ai.mock.MockLLMProvider;
import com.javadsamastery.ai.service.impl.LLMAnswerEvaluator;
import com.javadsamastery.ai.service.impl.LLMQuestionGenerator;
import com.javadsamastery.ai.service.impl.LLMTutor;
import com.javadsamastery.domain.content.QuestionBank;
import com.javadsamastery.domain.content.QuestionDefinition;
import com.javadsamastery.domain.content.TopicLoader;
import com.javadsamastery.domain.content.TopicTemplate;
import com.javadsamastery.domain.topic.CurriculumGraph;
import com.javadsamastery.domain.curriculum.CurriculumLoader;
import com.javadsamastery.domain.curriculum.TopicDefinition;
import com.javadsamastery.domain.evidence.AttemptContext;
import com.javadsamastery.domain.evidence.EvaluationResult;
import com.javadsamastery.domain.evidence.EvidenceRecord;
import com.javadsamastery.domain.learner.LearnerProfile;
import com.javadsamastery.domain.learner.TopicState;
import com.javadsamastery.domain.lesson.LearningState;
import com.javadsamastery.domain.lesson.QuestionType;
import com.javadsamastery.domain.problem.ProblemDifficulty;
import com.javadsamastery.engine.AITutorEngine;
import com.javadsamastery.engine.MasteryEngine;
import com.javadsamastery.engine.QuestionSelectionResult;
import com.javadsamastery.engine.QuestionSelector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Optional;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SlidingWindowLessonTest {

    private CurriculumGraph curriculum;
    private TopicDefinition slidingWindowDef;
    private TopicTemplate slidingWindowTemplate;
    private QuestionBank questionBank;
    private QuestionSelector selector;
    
    private MasteryEngine masteryEngine;
    private LearnerProfile profile;

    @BeforeEach
    void setUp() throws Exception {
        curriculum = CurriculumLoader.loadCurriculum("/curriculum/curriculum.json");
        
        slidingWindowDef = curriculum.getDefinition("sliding-window");
        assertNotNull(slidingWindowDef, "Sliding window should be in curriculum");

        slidingWindowTemplate = TopicLoader.load("sliding-window");
        assertNotNull(slidingWindowTemplate, "Sliding window topic JSON should load successfully");

        questionBank = new QuestionBank();
        try (InputStream is = getClass().getResourceAsStream("/questions/sliding-window.json")) {
            questionBank.loadFromJsonStream(is);
        }
        selector = new QuestionSelector(questionBank);
        
        masteryEngine = new MasteryEngine();
        profile = new LearnerProfile("user123");
    }

    private EvidenceRecord pass(String questionId, QuestionType qt) {
        return new EvidenceRecord("user123", "sliding-window", questionId, ProblemDifficulty.MEDIUM, qt, 0, false, EvaluationResult.PASS, null, Instant.now());
    }

    private EvidenceRecord fail(String questionId, QuestionType qt, String misconception) {
        return new EvidenceRecord("user123", "sliding-window", questionId, ProblemDifficulty.MEDIUM, qt, 0, false, EvaluationResult.FAIL, misconception, Instant.now());
    }

    @Test
    void testSlidingWindowVerticalSlice_CompleteNominalPath() {
        LessonFlow flow = new LessonFlow(slidingWindowDef, slidingWindowTemplate, profile, masteryEngine);
        
        // 1. CONTENT_INTRO
        assertEquals(LearningState.CONTENT_INTRO, flow.getCurrentState());
        flow.advanceIntro();
        
        // 2. CONCEPT_ASSESSMENT
        assertEquals(LearningState.CONCEPT_ASSESSMENT, flow.getCurrentState());
        var qConcept = selector.selectNextQuestion(profile.getTopicState("sliding-window"), flow.getCurrentState());
        assertEquals("sw-recog-1", qConcept.selectedQuestion().questionId());
        EvidenceRecord e1 = pass(qConcept.selectedQuestion().questionId(), QuestionType.CONCEPT);
        flow.submitEvidence(e1);
        
        // 3. DERIVE
        assertEquals(LearningState.DERIVE, flow.getCurrentState());
        var qDerive = selector.selectNextQuestion(profile.getTopicState("sliding-window"), flow.getCurrentState());
        assertEquals("sw-derive-1", qDerive.selectedQuestion().questionId());
        EvidenceRecord e2 = pass(qDerive.selectedQuestion().questionId(), QuestionType.DERIVATION);
        flow.submitEvidence(e2);
        
        // 4. IMPLEMENTATION
        assertEquals(LearningState.IMPLEMENTATION, flow.getCurrentState());
        var qImpl = selector.selectNextQuestion(profile.getTopicState("sliding-window"), flow.getCurrentState());
        assertEquals("sw-impl-1", qImpl.selectedQuestion().questionId()); // Medium is standard
        EvidenceRecord e3 = pass(qImpl.selectedQuestion().questionId(), QuestionType.IMPLEMENTATION);
        flow.submitEvidence(e3);
        
        // 5. EXPLANATION
        assertEquals(LearningState.EXPLANATION, flow.getCurrentState());
        var qExpl = selector.selectNextQuestion(profile.getTopicState("sliding-window"), flow.getCurrentState());
        EvidenceRecord e4 = pass(qExpl.selectedQuestion().questionId(), QuestionType.COMPLEXITY);
        flow.submitEvidence(e4);
        
        // 6. COUNTEREXAMPLE
        assertEquals(LearningState.COUNTEREXAMPLE, flow.getCurrentState());
        var qCount = selector.selectNextQuestion(profile.getTopicState("sliding-window"), flow.getCurrentState());
        EvidenceRecord e5 = pass(qCount.selectedQuestion().questionId(), QuestionType.COUNTEREXAMPLE);
        flow.submitEvidence(e5);
        
        // 7. OPTIMIZE
        assertEquals(LearningState.OPTIMIZE, flow.getCurrentState());
        var qOpt = selector.selectNextQuestion(profile.getTopicState("sliding-window"), flow.getCurrentState());
        EvidenceRecord e6 = pass(qOpt.selectedQuestion().questionId(), QuestionType.OPTIMIZATION);
        flow.submitEvidence(e6);
        
        // 8. TRANSFER
        assertEquals(LearningState.TRANSFER, flow.getCurrentState());
        var qTrans = selector.selectNextQuestion(profile.getTopicState("sliding-window"), flow.getCurrentState());
        EvidenceRecord e7 = pass(qTrans.selectedQuestion().questionId(), QuestionType.TRANSFER);
        flow.submitEvidence(e7);
        
        // 9. COMPLETED
        assertEquals(LearningState.COMPLETED, flow.getCurrentState());
        
        // Verify Mastery
        TopicState finalState = profile.getTopicState("sliding-window");
        assertEquals(com.javadsamastery.domain.topic.TopicMastery.PROFICIENT, finalState.getMastery());
    }

    @Test
    void testSlidingWindow_FailureAndRemediationWithMisconception() {
        LessonFlow flow = new LessonFlow(slidingWindowDef, slidingWindowTemplate, profile, masteryEngine);
        flow.advanceIntro();
        
        // CONCEPT
        var q1 = selector.selectNextQuestion(profile.getTopicState("sliding-window"), flow.getCurrentState());
        flow.submitEvidence(pass(q1.selectedQuestion().questionId(), QuestionType.CONCEPT));
        
        // DERIVE (Fail once with misconception)
        assertEquals(LearningState.DERIVE, flow.getCurrentState());
        var q2 = selector.selectNextQuestion(profile.getTopicState("sliding-window"), flow.getCurrentState());
        flow.submitEvidence(fail(q2.selectedQuestion().questionId(), QuestionType.DERIVATION, "WRONG_CONTRACTION"));
        
        // State should now be REMEDIATION
        assertEquals(LearningState.REMEDIATION, flow.getCurrentState());
        assertTrue(profile.getTopicState("sliding-window").getActiveMisconceptions().contains("WRONG_CONTRACTION"));
        
        // Next question should target WRONG_CONTRACTION. 
        // We pass the nominal objective (DERIVE) to the selector, which uses the state to find the remediation question.
        var qRem = selector.selectNextQuestion(profile.getTopicState("sliding-window"), LearningState.DERIVE);
        assertEquals("sw-misc-wrong-contraction", qRem.selectedQuestion().questionId(), "Should pick the misconception-targeted question");
        
        flow.submitEvidence(pass(qRem.selectedQuestion().questionId(), QuestionType.DERIVATION));
        
        // Should clear misconception and return to DERIVE (since we passed remediation)
        assertFalse(profile.getTopicState("sliding-window").getActiveMisconceptions().contains("WRONG_CONTRACTION"));
        assertEquals(LearningState.DERIVE, flow.getCurrentState());
    }

    @Test
    void testAiSecurity_CannotForcePassOnImplementation() {
        LessonFlow flow = new LessonFlow(slidingWindowDef, slidingWindowTemplate, profile, masteryEngine);
        flow.advanceIntro();
        
        // Jump to implementation by passing concept/derive
        flow.submitEvidence(pass("qC", QuestionType.CONCEPT));
        flow.submitEvidence(pass("qD", QuestionType.DERIVATION));
        
        assertEquals(LearningState.IMPLEMENTATION, flow.getCurrentState());
        var qI = selector.selectNextQuestion(profile.getTopicState("sliding-window"), flow.getCurrentState());
        
        // AI hallucinates a PASS but we enforce FAIL via EvidencePolicy
        // For testing the loop, we simulate EvidencePolicy overriding the FAIL
        AttemptContext ctx = new AttemptContext("atmpt1", "sliding-window", qI.selectedQuestion().questionId(), LearningState.IMPLEMENTATION, QuestionType.IMPLEMENTATION, ProblemDifficulty.MEDIUM, "user123", null);
        AIAssessment assessment = new AIAssessment("atmpt1", "sliding-window", qI.selectedQuestion().questionId(), EvaluationResult.PASS, "Good code!", null);
        
        EvidenceRecord ev = com.javadsamastery.domain.evidence.EvidencePolicy.authorize(assessment, ctx, 0, false, null); // false = deterministic tests failed
        
        // EvidencePolicy must enforce FAIL
        assertEquals(EvaluationResult.FAIL, ev.result());
        
        flow.submitEvidence(ev);
        // LessonState should transition to DEBUGGING, not EXPLANATION
        assertEquals(LearningState.DEBUGGING, flow.getCurrentState());
    }
}
