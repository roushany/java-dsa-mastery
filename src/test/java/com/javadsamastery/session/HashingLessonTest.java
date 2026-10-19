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

class HashingLessonTest {

    @Test
    void testHashingVerticalSlice_CompleteNominalPath() {
        TopicTemplate template = TopicLoader.load("hashing");
        assertEquals("hashing", template.topicId());

        LearnerProfile profile = new LearnerProfile("learner-h");
        MasteryEngine engine = new MasteryEngine();
        var graph = com.javadsamastery.domain.curriculum.CurriculumLoader.loadCurriculum("/curriculum/curriculum.json");
        var topicDef = graph.getDefinition("hashing");
        LessonFlow flow = new LessonFlow(topicDef, template, profile, engine);

        assertEquals(LearningState.CONTENT_INTRO, flow.getCurrentState());
        flow.advanceIntro();

        // 1. Concept
        assertEquals(LearningState.CONCEPT_ASSESSMENT, flow.getCurrentState());
        flow.submitEvidence(rec("learner-h", "h-c", QuestionType.CONCEPT, EvaluationResult.PASS));

        // 2. Derive
        assertEquals(LearningState.DERIVE, flow.getCurrentState());
        flow.submitEvidence(rec("learner-h", "h-d", QuestionType.DERIVATION, EvaluationResult.PASS));

        // 3. Implementation
        assertEquals(LearningState.IMPLEMENTATION, flow.getCurrentState());
        flow.submitEvidence(rec("learner-h", "h-i", QuestionType.IMPLEMENTATION, EvaluationResult.PASS));

        // 4. Explanation
        assertEquals(LearningState.EXPLANATION, flow.getCurrentState());
        flow.submitEvidence(rec("learner-h", "h-e", QuestionType.CORRECTNESS, EvaluationResult.PASS));

        // 5. Counterexample
        assertEquals(LearningState.COUNTEREXAMPLE, flow.getCurrentState());
        flow.submitEvidence(rec("learner-h", "h-ce", QuestionType.COUNTEREXAMPLE, EvaluationResult.PASS));

        // 6. Optimize (Hashing has optimize in default curriculum? Let's check curriculum.json)
        // Default stageRequirements allows OPTIMIZE unless disabled
        assertEquals(LearningState.OPTIMIZE, flow.getCurrentState());
        flow.submitEvidence(rec("learner-h", "h-o", QuestionType.OPTIMIZATION, EvaluationResult.PASS));

        // 7. Transfer
        assertEquals(LearningState.TRANSFER, flow.getCurrentState());
        flow.submitEvidence(rec("learner-h", "h-t", QuestionType.TRANSFER, EvaluationResult.PASS));

        // 8. Retrieval
        assertEquals(LearningState.RETRIEVAL, flow.getCurrentState());
        flow.submitEvidence(rec("learner-h", "h-r", QuestionType.TRANSFER, EvaluationResult.PASS));

        // 9. COMPLETED
        assertEquals(LearningState.COMPLETED, flow.getCurrentState());

        // Verify Mastery
        assertEquals(TopicMastery.PROFICIENT, profile.getMastery("hashing"));
    }

    private EvidenceRecord rec(String uId, String qId, QuestionType qt, EvaluationResult res) {
        return new EvidenceRecord(uId, "hashing", qId, ProblemDifficulty.MEDIUM, qt, 0, false, res, null, Instant.now());
    }
}
