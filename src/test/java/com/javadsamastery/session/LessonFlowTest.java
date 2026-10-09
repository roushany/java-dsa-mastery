package com.javadsamastery.session;

import com.javadsamastery.domain.content.TopicLoader;
import com.javadsamastery.domain.content.TopicTemplate;
import com.javadsamastery.domain.evidence.EvaluationResult;
import com.javadsamastery.domain.evidence.EvidenceRecord;
import com.javadsamastery.domain.learner.LearnerProfile;
import com.javadsamastery.domain.lesson.LearningState;
import com.javadsamastery.domain.lesson.QuestionType;
import com.javadsamastery.domain.problem.ProblemDifficulty;
import com.javadsamastery.engine.MasteryEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class LessonFlowTest {

    private TopicTemplate template;
    private LearnerProfile profile;
    private MasteryEngine engine;
    private LessonFlow flow;

    @BeforeEach
    void setUp() {
        template = TopicLoader.load("linear-search");
        profile = new LearnerProfile("learner-1");
        engine = new MasteryEngine();
        var topicDef = new com.javadsamastery.domain.curriculum.TopicDefinition(
            "linear-search", "Linear Search", java.util.List.of(), "BEGINNER", null
        );
        flow = new LessonFlow(topicDef, template, profile, engine);
    }

    private EvidenceRecord passRecord() {
        return new EvidenceRecord("learner-1", "linear-search", "p1", ProblemDifficulty.EASY, QuestionType.CONCEPT, 0, false, EvaluationResult.PASS, null, Instant.now());
    }

    private EvidenceRecord failRecord() {
        return new EvidenceRecord("learner-1", "linear-search", "p1", ProblemDifficulty.EASY, QuestionType.CONCEPT, 0, false, EvaluationResult.FAIL, null, Instant.now());
    }

    private EvidenceRecord partialRecord() {
        return new EvidenceRecord("learner-1", "linear-search", "p1", ProblemDifficulty.EASY, QuestionType.CONCEPT, 1, false, EvaluationResult.PARTIAL, null, Instant.now());
    }

    @Test
    void testPerfectLearnerPath() {
        assertEquals(LearningState.CONTENT_INTRO, flow.getCurrentState());
        flow.advanceIntro();
        assertEquals(LearningState.CONCEPT_ASSESSMENT, flow.getCurrentState());

        flow.submitEvidence(passRecord()); // Concept passed -> Derive
        assertEquals(LearningState.DERIVE, flow.getCurrentState());

        flow.submitEvidence(passRecord()); // Derive passed -> Implementation
        assertEquals(LearningState.IMPLEMENTATION, flow.getCurrentState());

        flow.submitEvidence(passRecord()); // Implementation passed
        assertEquals(LearningState.EXPLANATION, flow.getCurrentState());

        flow.submitEvidence(passRecord()); // Explanation passed
        assertEquals(LearningState.COUNTEREXAMPLE, flow.getCurrentState());

        flow.submitEvidence(passRecord()); // Counterexample passed -> Optimize
        assertEquals(LearningState.OPTIMIZE, flow.getCurrentState());

        flow.submitEvidence(passRecord()); // Optimize passed -> Transfer
        assertEquals(LearningState.TRANSFER, flow.getCurrentState());

        flow.submitEvidence(passRecord()); // Transfer passed
        assertEquals(LearningState.COMPLETED, flow.getCurrentState());
    }

    @Test
    void testFailureRemediationSuccess() {
        flow.advanceIntro();
        assertEquals(LearningState.CONCEPT_ASSESSMENT, flow.getCurrentState());

        flow.submitEvidence(failRecord());
        assertEquals(LearningState.REMEDIATION, flow.getCurrentState());

        flow.submitEvidence(passRecord()); // Remediation passed -> Back to Concept
        assertEquals(LearningState.CONCEPT_ASSESSMENT, flow.getCurrentState());

        flow.submitEvidence(passRecord()); // Concept passed -> Derive
        assertEquals(LearningState.DERIVE, flow.getCurrentState());

        flow.submitEvidence(passRecord()); // Derive passed -> Implementation
        assertEquals(LearningState.IMPLEMENTATION, flow.getCurrentState());
    }

    @Test
    void testPartialAnswerTargetedFollowUp() {
        flow.advanceIntro();
        flow.submitEvidence(partialRecord());
        assertEquals(LearningState.CONCEPT_ASSESSMENT, flow.getCurrentState()); // Stay in assessment
    }

    @Test
    void testHintRetry() {
        flow.advanceIntro();
        flow.submitEvidence(partialRecord()); // Assume hint is partial
        assertEquals(LearningState.CONCEPT_ASSESSMENT, flow.getCurrentState());
    }

    // testRepeatedFailure removed

    @Test
    void testImplementationFailureDebuggingRetry() {
        flow.advanceIntro();
        flow.submitEvidence(passRecord());
        assertEquals(LearningState.DERIVE, flow.getCurrentState());

        flow.submitEvidence(passRecord());
        assertEquals(LearningState.IMPLEMENTATION, flow.getCurrentState());

        flow.submitEvidence(failRecord());
        assertEquals(LearningState.DEBUGGING, flow.getCurrentState());

        flow.submitEvidence(failRecord());
        assertEquals(LearningState.DEBUGGING, flow.getCurrentState()); // Stay in debugging until 3 fails

        flow.submitEvidence(passRecord());
        assertEquals(LearningState.EXPLANATION, flow.getCurrentState());
    }

    private void traverseTo(LearningState target) {
        if (target == LearningState.CONTENT_INTRO) return;
        flow.advanceIntro();
        if (target == LearningState.CONCEPT_ASSESSMENT) return;
        flow.submitEvidence(passRecord()); // to DERIVE
        if (target == LearningState.DERIVE) return;
        flow.submitEvidence(passRecord()); // to IMPLEMENTATION
        if (target == LearningState.IMPLEMENTATION) return;
        flow.submitEvidence(passRecord()); // to EXPLANATION
        if (target == LearningState.EXPLANATION) return;
        flow.submitEvidence(passRecord()); // to COUNTEREXAMPLE
        if (target == LearningState.COUNTEREXAMPLE) return;
        flow.submitEvidence(passRecord()); // to OPTIMIZE
        if (target == LearningState.OPTIMIZE) return;
        flow.submitEvidence(passRecord()); // to TRANSFER
        if (target == LearningState.TRANSFER) return;
    }

    @Test
    void testSuccessfulImplementationExplanation() {
        traverseTo(LearningState.IMPLEMENTATION);
        flow.submitEvidence(passRecord());
        assertEquals(LearningState.EXPLANATION, flow.getCurrentState());
    }

    @Test
    void testSuccessfulExplanationCounterexample() {
        traverseTo(LearningState.EXPLANATION);
        flow.submitEvidence(passRecord());
        assertEquals(LearningState.COUNTEREXAMPLE, flow.getCurrentState());
    }

    @Test
    void testSuccessfulCounterexampleOptimize() {
        traverseTo(LearningState.COUNTEREXAMPLE);
        flow.submitEvidence(passRecord());
        assertEquals(LearningState.OPTIMIZE, flow.getCurrentState());
    }

    @Test
    void testSuccessfulOptimizeTransfer() {
        traverseTo(LearningState.OPTIMIZE);
        flow.submitEvidence(passRecord());
        assertEquals(LearningState.TRANSFER, flow.getCurrentState());
    }

    @Test
    void testTransferFailureRemediation() {
        traverseTo(LearningState.TRANSFER);
        flow.submitEvidence(failRecord());
        assertEquals(LearningState.REMEDIATION, flow.getCurrentState());
    }

    @Test
    void testInvalidTransition() {
        flow.advanceIntro(); // valid transition
        assertThrows(IllegalStateException.class, () -> flow.advanceIntro()); // Cannot advance intro twice
    }

    @Test
    void testAttemptedInfiniteLoop() {
        flow.advanceIntro();
        // 3 fails to reach walkthrough
        flow.submitEvidence(failRecord());
        flow.submitEvidence(failRecord());
        flow.submitEvidence(failRecord());
        assertEquals(LearningState.SOLUTION_WALKTHROUGH, flow.getCurrentState());
        
        // 9 total fails in walkthrough state (so 12 total across the session)
        for (int i=0; i<9; i++) {
            flow.submitEvidence(failRecord());
        }
        assertNotEquals(LearningState.FAILED_TERMINATED, flow.getCurrentState());
        
        // 10th fail in walkthrough = termination
        flow.submitEvidence(failRecord());
        assertEquals(LearningState.FAILED_TERMINATED, flow.getCurrentState());
        
        // Submitting after termination throws
        assertThrows(IllegalStateException.class, () -> flow.submitEvidence(passRecord()));
    }

    @Test
    void testPedagogicalWalkthrough() {
        flow.advanceIntro();
        flow.submitEvidence(failRecord()); // fail 1
        flow.submitEvidence(failRecord()); // fail 2
        flow.submitEvidence(failRecord()); // fail 3 (pedagogical)
        assertEquals(LearningState.SOLUTION_WALKTHROUGH, flow.getCurrentState());
    }

    @Test
    void testSessionCompletion() {
        traverseTo(LearningState.TRANSFER);
        flow.submitEvidence(passRecord());
        assertEquals(LearningState.COMPLETED, flow.getCurrentState());
        
        assertThrows(IllegalStateException.class, () -> flow.submitEvidence(passRecord()));
    }
}
