package com.javadsamastery.session;

import com.javadsamastery.ai.mock.MockLLMProvider;
import com.javadsamastery.ai.service.impl.LLMAnswerEvaluator;
import com.javadsamastery.ai.service.impl.LLMQuestionGenerator;
import com.javadsamastery.ai.service.impl.LLMTutor;
import com.javadsamastery.domain.content.QuestionBank;
import com.javadsamastery.domain.content.TopicLoader;
import com.javadsamastery.domain.curriculum.CurriculumLoader;
import com.javadsamastery.domain.curriculum.TopicDefinition;
import com.javadsamastery.domain.evidence.EvidenceRecord;
import com.javadsamastery.domain.learner.LearnerProfile;
import com.javadsamastery.domain.lesson.LearningState;
import com.javadsamastery.domain.topic.TopicMastery;
import com.javadsamastery.engine.AITutorEngine;
import com.javadsamastery.engine.MasteryEngine;
import com.javadsamastery.engine.QuestionSelector;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CrossTopicSimulationTest {

    private static final Map<String, String> TOPIC_MISCONCEPTIONS = Map.of(
        "linear-search", "loop-syntax",
        "binary-search", "overflow",
        "two-pointers", "overlap",
        "sliding-window", "WRONG_CONTRACTION",
        "binary-search-on-answer", "BOUNDARY_UPDATE_ERROR"
    );

    private static final List<String> ALL_TOPICS = List.of(
        "linear-search", "binary-search", "two-pointers", "sliding-window", "prefix-sum", "hashing", "binary-search-on-answer"
    );

    private QuestionBank loadBank(String topicId) throws Exception {
        QuestionBank bank = new QuestionBank();
        try (InputStream is = getClass().getResourceAsStream("/questions/" + topicId + ".json")) {
            bank.loadFromJsonStream(is);
        }
        return bank;
    }

    private AITutorEngine createTutor(MockLLMProvider provider) {
        return new AITutorEngine(
            new LLMTutor(provider),
            new LLMQuestionGenerator(provider),
            new LLMAnswerEvaluator(provider)
        );
    }

    @Test
    void testProfileA_IndependentLearner() throws Exception {
        var graph = CurriculumLoader.loadCurriculum("/curriculum/curriculum.json");

        for (String topicId : ALL_TOPICS) {
            TopicDefinition topicDef = graph.getDefinition(topicId);
            var template = TopicLoader.load(topicId);
            QuestionBank bank = loadBank(topicId);

            MockLLMProvider provider = new MockLLMProvider();
            AITutorEngine tutor = createTutor(provider);

            LearnerProfile profile = new LearnerProfile("profile-a-" + topicId);
            MasteryEngine masteryEngine = new MasteryEngine();
            QuestionSelector selector = new QuestionSelector(bank);

            LearningSession session = new LearningSession(profile, masteryEngine, selector, tutor);
            LessonFlow flow = new LessonFlow(topicDef, template, profile, masteryEngine);

            session.start();
            provider.setCannedResponse(String.class, "{\"evaluation\": \"PASS\", \"confidenceScore\": 0, \"feedback\": \"Perfect!\"}");

            int steps = 0;
            while (flow.getCurrentState() != LearningState.COMPLETED && steps < 50) {
                steps++;
                LearningState state = flow.getCurrentState();
                if (state == LearningState.CONTENT_INTRO) {
                    flow.advanceIntro();
                    continue;
                }

                var question = session.getNextQuestion(topicId, state);

                EvidenceRecord ev = session.submitAnswer(question.expectedConcept(), "I derived the answer", 0, true);
                flow.submitEvidence(ev);
            }

            assertEquals(TopicMastery.MASTERED, profile.getMastery(topicId), "Profile A should achieve MASTERED in " + topicId);
            assertTrue(steps < 50, "Should complete without infinite looping");
        }
    }

    @Test
    void testProfileB_PersistentMisconception() throws Exception {
        var graph = CurriculumLoader.loadCurriculum("/curriculum/curriculum.json");

        for (String topicId : ALL_TOPICS) {
            TopicDefinition topicDef = graph.getDefinition(topicId);
            var template = TopicLoader.load(topicId);
            QuestionBank bank = loadBank(topicId);

            MockLLMProvider provider = new MockLLMProvider();
            AITutorEngine tutor = createTutor(provider);

            LearnerProfile profile = new LearnerProfile("profile-b-" + topicId);
            MasteryEngine masteryEngine = new MasteryEngine();
            QuestionSelector selector = new QuestionSelector(bank);

            LearningSession session = new LearningSession(profile, masteryEngine, selector, tutor);
            LessonFlow flow = new LessonFlow(topicDef, template, profile, masteryEngine);

            session.start();

            String targetMisconception = TOPIC_MISCONCEPTIONS.get(topicId);
            boolean hasRemediation = targetMisconception != null;

            boolean misconceptionSubmitted = false;
            boolean misconceptionCleared = false;

            int steps = 0;

            while (flow.getCurrentState() != LearningState.COMPLETED && steps < 50) {
                steps++;
                LearningState state = flow.getCurrentState();
                if (state == LearningState.CONTENT_INTRO) {
                    flow.advanceIntro();
                    continue;
                }

                var question = session.getNextQuestion(topicId, state);

                if ((state == LearningState.IMPLEMENTATION || state == LearningState.EXPLANATION || state == LearningState.DEBUGGING) && !misconceptionSubmitted) {
                    String miscToInject = hasRemediation ? targetMisconception : "FAKE_MISCONCEPTION";
                    provider.setCannedResponse(String.class, "{\"evaluation\": \"FAIL\", \"confidenceScore\": 0, \"misconceptionCategory\": \"" + miscToInject + "\", \"feedback\": \"Wrong.\"}");
                    EvidenceRecord ev = session.submitAnswer(question.expectedConcept(), "Bad answer", 0, true);
                    flow.submitEvidence(ev);

                    misconceptionSubmitted = true;

                    if (ev.misconceptionCategory() != null) {
                        assertTrue(profile.getTopicState(topicId).getActiveMisconceptions().contains(miscToInject));
                    } else if (!hasRemediation) {
                        assertFalse(profile.getTopicState(topicId).getActiveMisconceptions().contains(miscToInject),
                            "Unauthorized misconception must be safely rejected in " + topicId);
                    }
                } else if (misconceptionSubmitted && !misconceptionCleared && profile.getTopicState(topicId).getActiveMisconceptions().size() > 0) {
                    provider.setCannedResponse(String.class, "{\"evaluation\": \"PASS\", \"confidenceScore\": 0, \"feedback\": \"Fixed!\"}");
                    EvidenceRecord ev = session.submitAnswer(question.expectedConcept(), "Fixed answer", 0, true);
                    flow.submitEvidence(ev);
                    misconceptionCleared = true;
                } else {
                    provider.setCannedResponse(String.class, "{\"evaluation\": \"PASS\", \"confidenceScore\": 0, \"feedback\": \"Good!\"}");
                    EvidenceRecord ev = session.submitAnswer(question.expectedConcept(), "Good answer", 0, true);
                    flow.submitEvidence(ev);
                }
            }

            assertTrue(steps < 50, "Should complete without infinite looping in " + topicId);
            assertEquals(TopicMastery.MASTERED, profile.getMastery(topicId), "Profile B should recover and achieve MASTERED in " + topicId);
        }
    }

    @Test
    void testProfileC_HintDependentLearner() throws Exception {
        var graph = CurriculumLoader.loadCurriculum("/curriculum/curriculum.json");

        for (String topicId : ALL_TOPICS) {
            TopicDefinition topicDef = graph.getDefinition(topicId);
            var template = TopicLoader.load(topicId);
            QuestionBank bank = loadBank(topicId);

            MockLLMProvider provider = new MockLLMProvider();
            AITutorEngine tutor = createTutor(provider);

            LearnerProfile profile = new LearnerProfile("profile-c-" + topicId);
            MasteryEngine masteryEngine = new MasteryEngine();
            QuestionSelector selector = new QuestionSelector(bank);

            LearningSession session = new LearningSession(profile, masteryEngine, selector, tutor);
            LessonFlow flow = new LessonFlow(topicDef, template, profile, masteryEngine);

            session.start();
            provider.setCannedResponse(String.class, "{\"evaluation\": \"PASS\", \"confidenceScore\": 0, \"feedback\": \"Good!\"}");

            int steps = 0;
            while (flow.getCurrentState() != LearningState.COMPLETED && steps < 50) {
                steps++;
                LearningState state = flow.getCurrentState();
                if (state == LearningState.CONTENT_INTRO) {
                    flow.advanceIntro();
                    continue;
                }

                var question = session.getNextQuestion(topicId, state);

                int hints = (state == LearningState.IMPLEMENTATION || state == LearningState.TRANSFER) ? 2 : 0;

                EvidenceRecord ev = session.submitAnswer(question.expectedConcept(), "Hint assisted answer", hints, true);
                flow.submitEvidence(ev);
            }

            assertTrue(steps < 50, "Should complete without infinite looping in " + topicId);
            assertEquals(LearningState.COMPLETED, flow.getCurrentState());
            assertNotEquals(TopicMastery.MASTERED, profile.getMastery(topicId),
                "Hint-dependent learner should NOT receive independent mastery merely because assisted answers are correct in " + topicId);
        }
    }
}
