package com.javadsamastery.session;

import com.javadsamastery.domain.content.TopicLoader;
import com.javadsamastery.domain.content.TopicTemplate;
import com.javadsamastery.domain.evidence.EvaluationResult;
import com.javadsamastery.domain.evidence.EvidenceRecord;
import com.javadsamastery.domain.learner.LearnerProfile;
import com.javadsamastery.domain.lesson.LearningState;
import com.javadsamastery.domain.lesson.QuestionType;
import com.javadsamastery.domain.problem.ProblemDifficulty;
import com.javadsamastery.domain.topic.TopicMastery;
import com.javadsamastery.engine.MasteryEngine;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class LinearSearchLessonTest {

    @Test
    void testFullLinearSearchGuidedDiscoverySlice() {
        TopicTemplate template = TopicLoader.load("linear-search");
        assertEquals("linear-search", template.topicId());
        
        LearnerProfile profile = new LearnerProfile("learner-200");
        MasteryEngine engine = new MasteryEngine();
        var graph = com.javadsamastery.domain.curriculum.CurriculumLoader.loadCurriculum("/curriculum/curriculum.json");
        var topicDef = graph.getDefinition("linear-search");
        LessonFlow flow = new LessonFlow(topicDef, template, profile, engine);

        assertEquals(LearningState.CONTENT_INTRO, flow.getCurrentState());
        flow.advanceIntro();
        
        // Simulating the AI evaluating user inputs mapping to the new DAG
        // Concept Assessment
        assertEquals(LearningState.CONCEPT_ASSESSMENT, flow.getCurrentState());
        flow.submitEvidence(new EvidenceRecord("learner-200", "linear-search", "ls-1", ProblemDifficulty.EASY, QuestionType.CONCEPT, 0, false, EvaluationResult.PASS, null, Instant.now()));
        
        // Derive
        assertEquals(LearningState.DERIVE, flow.getCurrentState());
        flow.submitEvidence(new EvidenceRecord("learner-200", "linear-search", "ls-drv", ProblemDifficulty.EASY, QuestionType.DERIVATION, 0, false, EvaluationResult.PASS, null, Instant.now()));
        
        // Implementation
        assertEquals(LearningState.IMPLEMENTATION, flow.getCurrentState());
        flow.submitEvidence(new EvidenceRecord("learner-200", "linear-search", "ls-impl", ProblemDifficulty.EASY, QuestionType.IMPLEMENTATION, 0, false, EvaluationResult.PASS, null, Instant.now()));
        
        // Explanation / Complexity
        assertEquals(LearningState.EXPLANATION, flow.getCurrentState());
        flow.submitEvidence(new EvidenceRecord("learner-200", "linear-search", "ls-exp", ProblemDifficulty.EASY, QuestionType.COMPLEXITY, 0, false, EvaluationResult.PASS, null, Instant.now()));
        
        // Counterexample
        assertEquals(LearningState.COUNTEREXAMPLE, flow.getCurrentState());
        flow.submitEvidence(new EvidenceRecord("learner-200", "linear-search", "ls-ce", ProblemDifficulty.EASY, QuestionType.COUNTEREXAMPLE, 0, false, EvaluationResult.PASS, null, Instant.now()));
        
        // Transfer
        assertEquals(LearningState.TRANSFER, flow.getCurrentState());
        flow.submitEvidence(new EvidenceRecord("learner-200", "linear-search", "ls-tr", ProblemDifficulty.EASY, QuestionType.TRANSFER, 0, false, EvaluationResult.PASS, null, Instant.now()));
        
        // Final State
        assertEquals(LearningState.COMPLETED, flow.getCurrentState());
        
        // Mastery should be established as PROFICIENT
        assertEquals(TopicMastery.PROFICIENT, profile.getMastery("linear-search"));
        assertEquals(6, flow.getHistory().size()); // Intro doesn't log an EvidenceRecord, 6 items
    }
}
