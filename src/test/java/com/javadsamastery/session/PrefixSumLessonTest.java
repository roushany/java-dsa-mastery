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

class PrefixSumLessonTest {

    @Test
    void testPrefixSumVerticalSlice_CompleteNominalPath() {
        TopicTemplate template = TopicLoader.load("prefix-sum");
        assertEquals("prefix-sum", template.topicId());

        LearnerProfile profile = new LearnerProfile("learner-ps");
        MasteryEngine engine = new MasteryEngine();
        var graph = com.javadsamastery.domain.curriculum.CurriculumLoader.loadCurriculum("/curriculum/curriculum.json");
        var topicDef = graph.getDefinition("prefix-sum");
        LessonFlow flow = new LessonFlow(topicDef, template, profile, engine);

        assertEquals(LearningState.CONTENT_INTRO, flow.getCurrentState());
        flow.advanceIntro();

        // 1. Concept
        assertEquals(LearningState.CONCEPT_ASSESSMENT, flow.getCurrentState());
        flow.submitEvidence(rec("learner-ps", "ps-c", QuestionType.CONCEPT, EvaluationResult.PASS));

        // 2. Derive
        assertEquals(LearningState.DERIVE, flow.getCurrentState());
        flow.submitEvidence(rec("learner-ps", "ps-d", QuestionType.DERIVATION, EvaluationResult.PASS));

        // 3. Implementation
        assertEquals(LearningState.IMPLEMENTATION, flow.getCurrentState());
        flow.submitEvidence(rec("learner-ps", "ps-i", QuestionType.IMPLEMENTATION, EvaluationResult.PASS));

        // 4. Explanation
        assertEquals(LearningState.EXPLANATION, flow.getCurrentState());
        flow.submitEvidence(rec("learner-ps", "ps-e", QuestionType.COMPLEXITY, EvaluationResult.PASS));

        // 5. Counterexample
        assertEquals(LearningState.COUNTEREXAMPLE, flow.getCurrentState());
        flow.submitEvidence(rec("learner-ps", "ps-ce", QuestionType.COUNTEREXAMPLE, EvaluationResult.PASS));

        // 6. Optimize (Prefix Sum has optimize in default curriculum)
        assertEquals(LearningState.OPTIMIZE, flow.getCurrentState());
        flow.submitEvidence(rec("learner-ps", "ps-o", QuestionType.OPTIMIZATION, EvaluationResult.PASS));

        // 7. Transfer
        assertEquals(LearningState.TRANSFER, flow.getCurrentState());
        flow.submitEvidence(rec("learner-ps", "ps-t", QuestionType.TRANSFER, EvaluationResult.PASS));

        // 8. Retrieval
        assertEquals(LearningState.RETRIEVAL, flow.getCurrentState());
        flow.submitEvidence(rec("learner-ps", "ps-r", QuestionType.TRANSFER, EvaluationResult.PASS));

        // 9. COMPLETED
        assertEquals(LearningState.COMPLETED, flow.getCurrentState());

        // Verify Mastery
        assertEquals(TopicMastery.PROFICIENT, profile.getMastery("prefix-sum"));
    }

    private EvidenceRecord rec(String uId, String qId, QuestionType qt, EvaluationResult res) {
        return new EvidenceRecord(uId, "prefix-sum", qId, ProblemDifficulty.MEDIUM, qt, 0, false, res, null, Instant.now());
    }
}
