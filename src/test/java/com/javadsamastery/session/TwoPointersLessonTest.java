package com.javadsamastery.session;

import com.javadsamastery.domain.content.TopicLoader;
import com.javadsamastery.domain.content.TopicTemplate;
import com.javadsamastery.domain.topic.CurriculumGraph;
import com.javadsamastery.domain.curriculum.CurriculumLoader;
import com.javadsamastery.domain.curriculum.TopicDefinition;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TwoPointersLessonTest {

    private CurriculumGraph graph;
    private TopicTemplate template;
    private LearnerProfile profile;
    private MasteryEngine engine;
    private LessonFlow flow;

    @BeforeEach
    void setUp() {
        graph = CurriculumLoader.loadCurriculum("/curriculum/curriculum.json");
        template = TopicLoader.load("two-pointers");
        profile = new LearnerProfile("learner-tp");
        engine = new MasteryEngine();
        
        TopicDefinition topicDef = graph.getDefinition("two-pointers");
        assertNotNull(topicDef, "Two Pointers must exist in curriculum");
        
        flow = new LessonFlow(topicDef, template, profile, engine);
    }

    private EvidenceRecord rec(EvaluationResult result, QuestionType type) {
        return new EvidenceRecord("learner-tp", "two-pointers", "tp-q1", ProblemDifficulty.MEDIUM, type, 0, false, result, null, Instant.now());
    }

    @Test
    void test1_DataLoadsCorrectly() {
        assertEquals("Two Pointers", template.title());
        assertNotNull(template.derivation());
        assertNotNull(template.transferProblem());
        
        TopicDefinition def = graph.getDefinition("two-pointers");
        assertTrue(def.prerequisites().contains("binary-search"), "Prerequisite should resolve to binary-search based on curriculum.json");
    }

    @Test
    void test2_FullLessonExecution() {
        // Starts at Intro
        assertEquals(LearningState.CONTENT_INTRO, flow.getCurrentState());
        flow.advanceIntro();
        
        // Concept
        assertEquals(LearningState.CONCEPT_ASSESSMENT, flow.getCurrentState());
        flow.submitEvidence(rec(EvaluationResult.PASS, QuestionType.CONCEPT));
        
        // Derive
        assertEquals(LearningState.DERIVE, flow.getCurrentState());
        flow.submitEvidence(rec(EvaluationResult.PASS, QuestionType.DERIVATION));
        
        // Implementation
        assertEquals(LearningState.IMPLEMENTATION, flow.getCurrentState());
        flow.submitEvidence(rec(EvaluationResult.PASS, QuestionType.IMPLEMENTATION));
        
        // Explanation
        assertEquals(LearningState.EXPLANATION, flow.getCurrentState());
        flow.submitEvidence(rec(EvaluationResult.PASS, QuestionType.CONCEPT)); // Assuming Explanation maps to CONCEPT type for now
        
        // Counterexample
        assertEquals(LearningState.COUNTEREXAMPLE, flow.getCurrentState());
        flow.submitEvidence(rec(EvaluationResult.PASS, QuestionType.COUNTEREXAMPLE));
        
        // Optimize
        assertEquals(LearningState.OPTIMIZE, flow.getCurrentState());
        flow.submitEvidence(rec(EvaluationResult.PASS, QuestionType.OPTIMIZATION));
        
        // Transfer
        assertEquals(LearningState.TRANSFER, flow.getCurrentState());
        flow.submitEvidence(rec(EvaluationResult.PASS, QuestionType.TRANSFER));
        
        // Completed
        assertEquals(LearningState.COMPLETED, flow.getCurrentState());
    }

    @Test
    void test3_DerivationFailureTriggersRemediation() {
        flow.advanceIntro();
        flow.submitEvidence(rec(EvaluationResult.PASS, QuestionType.CONCEPT));
        
        assertEquals(LearningState.DERIVE, flow.getCurrentState());
        
        // Fails derivation
        flow.submitEvidence(rec(EvaluationResult.FAIL, QuestionType.DERIVATION));
        
        assertEquals(LearningState.REMEDIATION, flow.getCurrentState());
        
        // Recovers
        flow.submitEvidence(rec(EvaluationResult.PASS, QuestionType.DERIVATION));
        assertEquals(LearningState.DERIVE, flow.getCurrentState());
    }
    
    @Test
    void test4_SecurityBoundary_AICannotForceAdvance() {
        flow.advanceIntro();
        
        // AI returns INVALID_RESPONSE due to EvidencePolicy rejecting an out-of-bounds assessment
        // Flow receives INVALID_RESPONSE
        flow.submitEvidence(rec(EvaluationResult.INVALID_RESPONSE, QuestionType.CONCEPT));
        
        // Should NOT advance
        assertEquals(LearningState.CONCEPT_ASSESSMENT, flow.getCurrentState());
    }

    @Test
    void test5_Architecture_SkipStageModifiesPlanWithoutCodeChanges() {
        // Create a custom TopicDefinition that skips OPTIMIZE, just like linear-search did.
        TopicDefinition customDef = new TopicDefinition(
            "two-pointers", 
            "Two Pointers", 
            List.of(), 
            "INTERMEDIATE", 
            Map.of(LearningState.OPTIMIZE, com.javadsamastery.domain.curriculum.StageRequirement.NOT_APPLICABLE)
        );
        
        LessonFlow customFlow = new LessonFlow(customDef, template, profile, engine);
        
        customFlow.advanceIntro(); // CONCEPT_ASSESSMENT
        customFlow.submitEvidence(rec(EvaluationResult.PASS, QuestionType.CONCEPT)); // DERIVE
        customFlow.submitEvidence(rec(EvaluationResult.PASS, QuestionType.DERIVATION)); // IMPLEMENTATION
        customFlow.submitEvidence(rec(EvaluationResult.PASS, QuestionType.IMPLEMENTATION)); // EXPLANATION
        customFlow.submitEvidence(rec(EvaluationResult.PASS, QuestionType.CONCEPT)); // COUNTEREXAMPLE
        customFlow.submitEvidence(rec(EvaluationResult.PASS, QuestionType.COUNTEREXAMPLE)); // TRANSFER (Skips OPTIMIZE)
        
        assertEquals(LearningState.TRANSFER, customFlow.getCurrentState(), "Should skip OPTIMIZE and go straight to TRANSFER");
    }
}
