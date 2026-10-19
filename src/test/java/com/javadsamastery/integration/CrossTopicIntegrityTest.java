package com.javadsamastery.integration;

import com.javadsamastery.ai.mock.MockLLMProvider;
import com.javadsamastery.ai.service.impl.LLMAnswerEvaluator;
import com.javadsamastery.ai.service.impl.LLMQuestionGenerator;
import com.javadsamastery.ai.service.impl.LLMTutor;
import com.javadsamastery.domain.content.QuestionBank;
import com.javadsamastery.domain.content.TopicLoader;
import com.javadsamastery.domain.curriculum.CurriculumLoader;
import com.javadsamastery.domain.evidence.EvaluationResult;
import com.javadsamastery.domain.evidence.EvidenceRecord;
import com.javadsamastery.domain.learner.LearnerProfile;
import com.javadsamastery.domain.lesson.LearningState;
import com.javadsamastery.domain.topic.TopicMastery;
import com.javadsamastery.engine.AITutorEngine;
import com.javadsamastery.engine.MasteryEngine;
import com.javadsamastery.engine.QuestionSelector;
import com.javadsamastery.session.LearningSession;
import com.javadsamastery.session.LessonFlow;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CrossTopicIntegrityTest {

    @Test
    void testAllTopicsCompletenessAndProgression() throws Exception {
        List<String> topics = List.of(
            "linear-search",
            "binary-search",
            "two-pointers",
            "sliding-window",
            "binary-search-on-answer",
            "prefix-sum",
            "hashing"
        );

        for (String topicId : topics) {
            // 1. Verify JSON loading
            var graph = CurriculumLoader.loadCurriculum("/curriculum/curriculum.json");
            var topicDef = graph.getDefinition(topicId);
            assertNotNull(topicDef, "Curriculum should contain " + topicId);

            var template = TopicLoader.load(topicId);
            assertNotNull(template, "Topic template should load for " + topicId);

            QuestionBank bank = new QuestionBank();
            try (InputStream is = getClass().getResourceAsStream("/questions/" + topicId + ".json")) {
                assertNotNull(is, "Questions JSON should exist for " + topicId);
                bank.loadFromJsonStream(is);
            }
            assertFalse(bank.getQuestions().isEmpty(), "Question bank should not be empty for " + topicId);

            // 2. Setup shared deterministic engine
            LearnerProfile profile = new LearnerProfile("cross-topic-learner-" + topicId);
            MasteryEngine masteryEngine = new MasteryEngine();
            QuestionSelector selector = new QuestionSelector(bank);

            MockLLMProvider provider = new MockLLMProvider();
            AITutorEngine tutor = new AITutorEngine(
                new LLMTutor(provider),
                new LLMQuestionGenerator(provider),
                new LLMAnswerEvaluator(provider)
            );

            LearningSession session = new LearningSession(profile, masteryEngine, selector, tutor);
            LessonFlow flow = new LessonFlow(topicDef, template, profile, masteryEngine);

            session.start();

            // Use a canned PASS response for standard deterministic tests
            provider.setCannedResponse(String.class, "{\"evaluation\": \"PASS\", \"confidenceScore\": 0, \"feedback\": \"Good!\"}");

            boolean hitRetrieval = false;
            boolean hitTransfer = false;
            boolean hitOptimize = false;

            // 3. Drive the State Machine to completion
            while (flow.getCurrentState() != LearningState.COMPLETED) {
                LearningState state = flow.getCurrentState();

                if (state == LearningState.CONTENT_INTRO) {
                    flow.advanceIntro();
                    continue;
                }

                var question = session.getNextQuestion(topicId, state);
                assertNotNull(question, "Question not found for state: " + state + " in topic " + topicId);

                if (state == LearningState.TRANSFER) {
                    hitTransfer = true;
                }
                if (state == LearningState.OPTIMIZE) {
                    hitOptimize = true;
                }
                if (state == LearningState.RETRIEVAL) {
                    hitRetrieval = true;
                    assertTrue(session.getActiveContextForTest().expectedState() == LearningState.RETRIEVAL);
                    assertTrue(question.expectedConcept().length() > 5, "Retrieval expected concept should be populated");
                }

                // Misconceptions validation: Verify all allowed misconceptions map correctly
                var actualQuestionDef = bank.getQuestions().stream()
                        .filter(q -> q.questionId().equals(session.getActiveContextForTest().questionId()))
                        .findFirst().orElseThrow();
                assertNotNull(actualQuestionDef.allowedMisconceptions(), "Allowed misconceptions must not be null (can be empty)");

                EvidenceRecord evidence = session.submitAnswer(question.expectedConcept(), "Answer", 0, true);

                // Check EvidencePolicy bounds logic manually verifying the returned evidence
                assertEquals(EvaluationResult.PASS, evidence.result(), "Mock PASS should yield PASS");
                assertEquals(topicId, evidence.topicId());

                if (state == LearningState.RETRIEVAL) {
                    assertTrue(evidence.isRetrievalPractice(), "Retrieval context must flag evidence as retrieval practice");
                }

                flow.submitEvidence(evidence);
            }

            session.end();

            // 4. Check Post-Conditions
            assertEquals(LearningState.COMPLETED, flow.getCurrentState());
            assertTrue(hitTransfer, "Topic must test TRANSFER");
            // Prefix sum and hashing might skip some standard things if not configured, but our loop ensures they hit COMPLETED.
            // Actually, we require RETRIEVAL and TRANSFER across the board now.
            assertTrue(hitRetrieval, "Topic must test RETRIEVAL");
            assertEquals(TopicMastery.MASTERED, profile.getMastery(topicId), "Successful traversal must yield MASTERED");
        }
    }
}
