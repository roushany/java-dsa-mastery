package com.javadsamastery.session;

import com.javadsamastery.domain.content.TopicLoader;
import com.javadsamastery.domain.content.TopicTemplate;
import com.javadsamastery.domain.evidence.EvaluationResult;
import com.javadsamastery.domain.evidence.EvidenceRecord;
import com.javadsamastery.domain.learner.LearnerProfile;
import com.javadsamastery.domain.lesson.LearningState;
import com.javadsamastery.domain.lesson.QuestionType;
import com.javadsamastery.domain.problem.ProblemDifficulty;
import com.javadsamastery.domain.topic.CurriculumFactory;
import com.javadsamastery.domain.topic.CurriculumGraph;
import com.javadsamastery.engine.MasteryEngine;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BinarySearchLessonTest {

    @Test
    void testBinarySearchIntegrationAndTransfer() {
        CurriculumGraph graph = CurriculumFactory.createStandardCurriculum();
        assertFalse(graph.canAccess("binary-search", Set.of())); // Fails if prerequisites missing
        
        // Learner has mastered Linear Search and its prerequisites
        assertTrue(graph.canAccess("binary-search", Set.of("foundations", "arrays", "strings", "linear-search")));

        TopicTemplate template = TopicLoader.load("binary-search");
        assertEquals("binary-search", template.topicId());
        
        LearnerProfile profile = new LearnerProfile("learner-300");
        MasteryEngine engine = new MasteryEngine();
        var topicDef = graph.getDefinition("binary-search");
        LessonFlow flow = new LessonFlow(topicDef, template, profile, engine);

        assertEquals(LearningState.CONTENT_INTRO, flow.getCurrentState());
        flow.advanceIntro();
        
        // Simulating the dynamic loop, including a failure and remediation
        assertEquals(LearningState.CONCEPT_ASSESSMENT, flow.getCurrentState());
        
        // Learner fails the concept of eliminating half the space
        flow.submitEvidence(new EvidenceRecord("learner-300", "binary-search", "bs-1", ProblemDifficulty.EASY, QuestionType.CONCEPT, 0, false, EvaluationResult.FAIL, null, Instant.now()));
        assertEquals(LearningState.REMEDIATION, flow.getCurrentState());
        
        // Learner passes remediation
        flow.submitEvidence(new EvidenceRecord("learner-300", "binary-search", "bs-1-rem", ProblemDifficulty.EASY, QuestionType.DEBUGGING, 0, false, EvaluationResult.PASS, null, Instant.now()));
        assertEquals(LearningState.CONCEPT_ASSESSMENT, flow.getCurrentState());
        
        // Learner passes concept
        flow.submitEvidence(new EvidenceRecord("learner-300", "binary-search", "bs-2", ProblemDifficulty.EASY, QuestionType.CONCEPT, 0, false, EvaluationResult.PASS, null, Instant.now()));
        assertEquals(LearningState.DERIVE, flow.getCurrentState());

        // Learner derives correctly
        flow.submitEvidence(new EvidenceRecord("learner-300", "binary-search", "bs-drv", ProblemDifficulty.MEDIUM, QuestionType.DERIVATION, 0, false, EvaluationResult.PASS, null, Instant.now()));
        assertEquals(LearningState.IMPLEMENTATION, flow.getCurrentState());
        
        // Learner implements correctly
        flow.submitEvidence(new EvidenceRecord("learner-300", "binary-search", "bs-impl", ProblemDifficulty.MEDIUM, QuestionType.IMPLEMENTATION, 0, false, EvaluationResult.PASS, null, Instant.now()));
        assertEquals(LearningState.EXPLANATION, flow.getCurrentState());
        
        // Learner passes explanation
        flow.submitEvidence(new EvidenceRecord("learner-300", "binary-search", "bs-exp", ProblemDifficulty.MEDIUM, QuestionType.COMPLEXITY, 0, false, EvaluationResult.PASS, null, Instant.now()));
        assertEquals(LearningState.COUNTEREXAMPLE, flow.getCurrentState());
        
        // Learner passes counterexample
        flow.submitEvidence(new EvidenceRecord("learner-300", "binary-search", "bs-ce", ProblemDifficulty.HARD, QuestionType.COUNTEREXAMPLE, 0, false, EvaluationResult.PASS, null, Instant.now()));
        assertEquals(LearningState.OPTIMIZE, flow.getCurrentState());

        // Learner optimizes correctly
        flow.submitEvidence(new EvidenceRecord("learner-300", "binary-search", "bs-opt", ProblemDifficulty.HARD, QuestionType.OPTIMIZATION, 0, false, EvaluationResult.PASS, null, Instant.now()));
        assertEquals(LearningState.TRANSFER, flow.getCurrentState());

        flow.submitEvidence(new EvidenceRecord("learner-300", "binary-search", "bs-tr", ProblemDifficulty.HARD, QuestionType.TRANSFER, 0, false, EvaluationResult.PASS, null, Instant.now()));
        assertEquals(LearningState.COMPLETED, flow.getCurrentState());
    }
}
