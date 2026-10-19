package com.javadsamastery.integration;

import com.javadsamastery.ai.mock.MockLLMProvider;
import com.javadsamastery.ai.service.impl.LLMAnswerEvaluator;
import com.javadsamastery.ai.service.impl.LLMQuestionGenerator;
import com.javadsamastery.ai.service.impl.LLMTutor;
import com.javadsamastery.domain.content.QuestionBank;
import com.javadsamastery.domain.content.TopicLoader;
import com.javadsamastery.domain.topic.CurriculumFactory;
import com.javadsamastery.domain.topic.CurriculumGraph;
import com.javadsamastery.domain.evidence.EvaluationResult;
import com.javadsamastery.domain.evidence.EvidenceRecord;
import com.javadsamastery.domain.learner.LearnerProfile;
import com.javadsamastery.domain.lesson.LearningState;
import com.javadsamastery.domain.lesson.QuestionType;
import com.javadsamastery.domain.problem.ProblemDifficulty;
import com.javadsamastery.domain.topic.TopicMastery;
import com.javadsamastery.engine.AITutorEngine;
import com.javadsamastery.engine.MasteryEngine;
import com.javadsamastery.engine.QuestionSelectionResult;
import com.javadsamastery.engine.QuestionSelector;
import com.javadsamastery.session.LearningSession;
import com.javadsamastery.session.LessonFlow;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SystemIntegrationTest {

    @Test
    void testEndToEndLearningSystem() {
        CurriculumGraph graph = CurriculumFactory.createStandardCurriculum();
        LearnerProfile profile = new LearnerProfile("int-test-learner");
        assertFalse(graph.canAccess("binary-search", Set.of()), "Cannot access binary search without prerequisites");
        QuestionBank bank = new QuestionBank();
        String json = "[\n" +
            "  {\n" +
            "    \"questionId\": \"q1\",\n" +
            "    \"topicId\": \"linear-search\",\n" +
            "    \"targetStage\": \"CONTENT_INTRO\",\n" +
            "    \"difficulty\": \"EASY\",\n" +
            "    \"content\": \"Intro\",\n" +
            "    \"expectedEvidence\": \"Evidence\",\n" +
            "    \"targetsMisconception\": null,\n" +
            "    \"isRemediation\": false,\n" +
            "    \"isTransfer\": false,\n" +
            "    \"isRetrieval\": false\n" +
            "  }\n" +
            "]";
        bank.loadFromJsonStream(new java.io.ByteArrayInputStream(json.getBytes()));
        QuestionSelector selector = new QuestionSelector(bank);
        QuestionSelectionResult selRes = selector.selectNextQuestion(profile.getTopicState("linear-search"), LearningState.CONTENT_INTRO);
        assertEquals("q1", selRes.selectedQuestion().questionId());

        MasteryEngine masteryEngine = new MasteryEngine();
        List<EvidenceRecord> linearSearchHistory = List.of(
            createRecord("linear-search", QuestionType.CONCEPT, false),
            createRecord("linear-search", QuestionType.CONCEPT, false),
            createRecord("linear-search", QuestionType.IMPLEMENTATION, false),
            createRecord("linear-search", QuestionType.IMPLEMENTATION, false),
            createRecord("linear-search", QuestionType.TRANSFER, false),
            createRecord("linear-search", QuestionType.TRANSFER, true)
        );
        masteryEngine.evaluateMastery(profile, "linear-search", linearSearchHistory);
        assertEquals(TopicMastery.MASTERED, profile.getMastery("linear-search"));
    }

    @Test
    void testFullSessionFlowWithRetrieval() throws Exception {
        CurriculumGraph graph = CurriculumFactory.createStandardCurriculum();
        var topicDef = com.javadsamastery.domain.curriculum.CurriculumLoader.loadCurriculum("/curriculum/curriculum.json").getDefinition("linear-search");
        var template = TopicLoader.load("linear-search");

        QuestionBank bank = new QuestionBank();
        try (InputStream is = getClass().getResourceAsStream("/questions/linear-search.json")) {
            bank.loadFromJsonStream(is);
        }

        LearnerProfile profile = new LearnerProfile("int-learner-full");
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

        provider.setCannedResponse(String.class, "{\"evaluation\": \"PASS\", \"confidenceScore\": 0, \"feedback\": \"Good!\"}");

        while (flow.getCurrentState() != LearningState.COMPLETED) {
            LearningState state = flow.getCurrentState();

            if (state == LearningState.CONTENT_INTRO) {
                flow.advanceIntro();
                continue;
            }

            var question = session.getNextQuestion("linear-search", state);
            assertNotNull(question, "Question should be found for state " + state);

            if (state == LearningState.RETRIEVAL) {
                assertEquals(LearningState.RETRIEVAL, session.getActiveContextForTest().expectedState());
            }

            EvidenceRecord evidence = session.submitAnswer(question.expectedConcept(), "I know the answer!", 0, true);
            flow.submitEvidence(evidence);
        }

        session.end();

        assertEquals(LearningState.COMPLETED, flow.getCurrentState());
        assertTrue(profile.getTopicState("linear-search").isTransferStrong());
        assertEquals(TopicMastery.MASTERED, profile.getMastery("linear-search"));
    }

    @Test
    void testPrefixSumOrdinarySessionAPI() throws Exception {
        var topicDef = com.javadsamastery.domain.curriculum.CurriculumLoader.loadCurriculum("/curriculum/curriculum.json").getDefinition("prefix-sum");
        var template = TopicLoader.load("prefix-sum");

        QuestionBank bank = new QuestionBank();
        try (InputStream is = getClass().getResourceAsStream("/questions/prefix-sum.json")) {
            bank.loadFromJsonStream(is);
        }

        LearnerProfile profile = new LearnerProfile("int-learner-prefixsum");
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
        provider.setCannedResponse(String.class, "{\"evaluation\": \"PASS\", \"confidenceScore\": 0, \"feedback\": \"Good!\"}");

        while (flow.getCurrentState() != LearningState.COMPLETED) {
            LearningState state = flow.getCurrentState();
            if (state == LearningState.CONTENT_INTRO) {
                flow.advanceIntro();
                continue;
            }
            var question = session.getNextQuestion("prefix-sum", state);
            assertNotNull(question, "Question not found for state: " + state);

            if (state == LearningState.RETRIEVAL) {
                assertTrue(session.getActiveContextForTest().expectedState() == LearningState.RETRIEVAL);
                assertEquals("ps-q8", session.getActiveContextForTest().questionId());
            }

            EvidenceRecord evidence = session.submitAnswer(question.expectedConcept(), "Answer", 0, true);
            flow.submitEvidence(evidence);

            if (state == LearningState.RETRIEVAL) {
                assertEquals(EvaluationResult.PASS, evidence.result());
                assertTrue(evidence.isRetrievalPractice());
            }
        }

        session.end();
        assertEquals(LearningState.COMPLETED, flow.getCurrentState());
        assertEquals(TopicMastery.MASTERED, profile.getMastery("prefix-sum"));
    }

    @Test
    void testHashingOrdinarySessionAPI() throws Exception {
        var topicDef = com.javadsamastery.domain.curriculum.CurriculumLoader.loadCurriculum("/curriculum/curriculum.json").getDefinition("hashing");
        var template = TopicLoader.load("hashing");

        QuestionBank bank = new QuestionBank();
        try (InputStream is = getClass().getResourceAsStream("/questions/hashing.json")) {
            bank.loadFromJsonStream(is);
        }

        LearnerProfile profile = new LearnerProfile("int-learner-hashing");
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
        provider.setCannedResponse(String.class, "{\"evaluation\": \"PASS\", \"confidenceScore\": 0, \"feedback\": \"Good!\"}");

        while (flow.getCurrentState() != LearningState.COMPLETED) {
            LearningState state = flow.getCurrentState();
            if (state == LearningState.CONTENT_INTRO) {
                flow.advanceIntro();
                continue;
            }
            var question = session.getNextQuestion("hashing", state);
            assertNotNull(question, "Question not found for state: " + state);

            if (state == LearningState.RETRIEVAL) {
                assertTrue(session.getActiveContextForTest().expectedState() == LearningState.RETRIEVAL);
                assertEquals("h-q8", session.getActiveContextForTest().questionId());
            }

            EvidenceRecord evidence = session.submitAnswer(question.expectedConcept(), "Answer", 0, true);
            flow.submitEvidence(evidence);

            if (state == LearningState.RETRIEVAL) {
                assertEquals(EvaluationResult.PASS, evidence.result());
                assertTrue(evidence.isRetrievalPractice());
            }
        }

        session.end();
        assertEquals(LearningState.COMPLETED, flow.getCurrentState());
        assertEquals(TopicMastery.MASTERED, profile.getMastery("hashing"));
    }

    private EvidenceRecord createRecord(String topic, QuestionType qt, boolean isRetrieval) {
        return new EvidenceRecord("int-test-learner", topic, "prob", ProblemDifficulty.EASY, qt, 0, isRetrieval, EvaluationResult.PASS, null, Instant.now());
    }
}
