package com.javadsamastery.domain.evidence;

import com.javadsamastery.ai.mock.MockLLMProvider;
import com.javadsamastery.ai.service.impl.LLMAnswerEvaluator;
import com.javadsamastery.ai.service.impl.LLMQuestionGenerator;
import com.javadsamastery.ai.service.impl.LLMTutor;
import com.javadsamastery.domain.content.TopicLoader;
import com.javadsamastery.domain.content.TopicTemplate;
import com.javadsamastery.domain.learner.LearnerProfile;
import com.javadsamastery.domain.lesson.LearningState;
import com.javadsamastery.domain.lesson.QuestionType;
import com.javadsamastery.domain.problem.ProblemDifficulty;
import com.javadsamastery.engine.AITutorEngine;

import com.javadsamastery.engine.MasteryEngine;
import com.javadsamastery.session.LearningSession;
import com.javadsamastery.session.LessonFlow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class EvidenceIntegrityTest {

    private MockLLMProvider llmProvider;
    private LearningSession session;
    private LessonFlow flow;

    @BeforeEach
    void setUp() {
        llmProvider = new MockLLMProvider();
        AITutorEngine tutor = new AITutorEngine(
            new LLMTutor(llmProvider), 
            new LLMQuestionGenerator(llmProvider), 
            new LLMAnswerEvaluator(llmProvider)
        );
        LearnerProfile profile = new LearnerProfile("u1");
        MasteryEngine masteryEngine = new MasteryEngine();
        
        com.javadsamastery.domain.content.QuestionBank bank = new com.javadsamastery.domain.content.QuestionBank();
        String json = """
            [
              {
                "questionId": "q1",
                "topicId": "linear-search",
                "targetStage": "CONCEPT_ASSESSMENT",
                "difficulty": "EASY",
                "content": "Intro",
                "expectedEvidence": "Evidence",
                "targetsMisconception": null,
                "isRemediation": false,
                "isTransfer": false,
                "isRetrieval": false
              }
            ]
            """;
        bank.loadFromJsonStream(new java.io.ByteArrayInputStream(json.getBytes()));
        com.javadsamastery.engine.QuestionSelector selector = new com.javadsamastery.engine.QuestionSelector(bank);
        
        session = new LearningSession(profile, masteryEngine, selector, tutor);
        session.start();

        TopicTemplate template = TopicLoader.load("linear-search");
        var graph = com.javadsamastery.domain.curriculum.CurriculumLoader.loadCurriculum("/curriculum/curriculum.json");
        var topicDef = graph.getDefinition("linear-search");
        flow = new LessonFlow(topicDef, template, profile, masteryEngine);
        flow.advanceIntro(); // Enters CONCEPT_ASSESSMENT
    }

    private EvidenceRecord simulateLearnerAnswer(String aiResponseJson, boolean codePassed) {
        // Set LLM response
        llmProvider.setCannedResponse(String.class, aiResponseJson);
        
        // Save the expected state before we fetch a new question (which currently resets it in our mock)
        LearningState currentState = flow.getCurrentState();
        
        // Learner asks for next question
        var q = session.getNextQuestion("linear-search", com.javadsamastery.domain.lesson.LearningState.CONCEPT_ASSESSMENT);
        
        // Update the context to reflect the actual FSM state
        session.setExpectedState(currentState);
        
        // Learner submits answer. The session validates the AI response against the current attempt context.
        EvidenceRecord record = session.submitAnswer(q.expectedConcept(), "Learner Answer", 0, codePassed);
        
        // Submit the resulting authoritative evidence to the State Machine
        flow.submitEvidence(record);
        
        return record;
    }

    @Test
    void test1_ValidAssessmentAdvancesState() {
        String validPass = "{\"evaluation\": \"PASS\", \"feedback\": \"Correct.\"}";
        simulateLearnerAnswer(validPass, false); // Concept doesn't need code execution
        
        // State should transition from CONCEPT_ASSESSMENT to DERIVE
        assertEquals(LearningState.DERIVE, flow.getCurrentState());
    }

    @Test
    void test2_StaleResponseReplayed() {
        // First attempt gets a question
        var q1 = session.getNextQuestion("linear-search", com.javadsamastery.domain.lesson.LearningState.CONCEPT_ASSESSMENT);
        
        // Create an AI assessment bound to q1's context
        com.javadsamastery.ai.dto.AIAssessment staleAssessment = new com.javadsamastery.ai.dto.AIAssessment(
            "stale-attempt-id", "linear-search", "q-1", EvaluationResult.PASS, "Good", null
        );
        
        // Try to push stale assessment via session's active context directly via Policy
        // Using overrideContext hook in session:
        session.overrideContext(new AttemptContext(
            "current-attempt", "linear-search", "q-2", LearningState.CONCEPT_ASSESSMENT, QuestionType.CONCEPT, ProblemDifficulty.MEDIUM, "u1", null
        ));
        
        EvidenceRecord record = EvidencePolicy.authorize(staleAssessment, new AttemptContext(
            "current-attempt", "linear-search", "q-2", LearningState.CONCEPT_ASSESSMENT, QuestionType.CONCEPT, ProblemDifficulty.MEDIUM, "u1", null
        ), 0, false, null);
        
        flow.submitEvidence(record);
        
        // Because attempt ID didn't match, it was rejected as INVALID_RESPONSE, state doesn't advance
        assertEquals(LearningState.CONCEPT_ASSESSMENT, flow.getCurrentState());
    }

    @Test
    void test3_MismatchedTopic() {
        // Learner is in linear-search. They try to replay a PASS from binary-search.
        com.javadsamastery.ai.dto.AIAssessment badTopicAssessment = new com.javadsamastery.ai.dto.AIAssessment(
            "current-attempt", "binary-search", "q-2", EvaluationResult.PASS, "Good", null
        );
        
        EvidenceRecord record = EvidencePolicy.authorize(badTopicAssessment, new AttemptContext(
            "current-attempt", "linear-search", "q-2", LearningState.CONCEPT_ASSESSMENT, QuestionType.CONCEPT, ProblemDifficulty.MEDIUM, "u1", null
        ), 0, false, null);
        
        flow.submitEvidence(record);
        assertEquals(LearningState.CONCEPT_ASSESSMENT, flow.getCurrentState());
    }

    @Test
    void test4_ImplementationEvidenceRequiresDeterministicPass() {
        // Advance to DERIVE, then IMPLEMENTATION state
        simulateLearnerAnswer("{\"evaluation\": \"PASS\", \"feedback\": \"Good\"}", false);
        assertEquals(LearningState.DERIVE, flow.getCurrentState());
        
        simulateLearnerAnswer("{\"evaluation\": \"PASS\", \"feedback\": \"Good\"}", false);
        assertEquals(LearningState.IMPLEMENTATION, flow.getCurrentState());
        
        session.setExpectedState(LearningState.IMPLEMENTATION); // Update context state to match flow
        
        // LLM says PASS, but code did NOT pass tests!
        simulateLearnerAnswer("{\"evaluation\": \"PASS\", \"feedback\": \"LGTM!\"}", false);
        
        // Because codeTestsPassed = false, EvidencePolicy overrides the AI and forces a FAIL.
        // It drops into DEBUGGING instead of advancing to EXPLANATION.
        assertEquals(LearningState.DEBUGGING, flow.getCurrentState());
    }

    @Test
    void test5_ImplementationEvidenceAdvancesWhenDeterministicTestsPass() {
        simulateLearnerAnswer("{\"evaluation\": \"PASS\", \"feedback\": \"Good\"}", false);
        assertEquals(LearningState.DERIVE, flow.getCurrentState());
        simulateLearnerAnswer("{\"evaluation\": \"PASS\", \"feedback\": \"Good\"}", false);
        assertEquals(LearningState.IMPLEMENTATION, flow.getCurrentState());
        
        session.setExpectedState(LearningState.IMPLEMENTATION); 
        
        // LLM says PASS AND code passes tests
        simulateLearnerAnswer("{\"evaluation\": \"PASS\", \"feedback\": \"LGTM!\"}", true);
        
        // State advances
        assertEquals(LearningState.EXPLANATION, flow.getCurrentState());
    }
    
    @Test
    void test6_TransferRequiresOriginality() {
        // Jump to Transfer
        while(flow.getCurrentState() != LearningState.TRANSFER && flow.getCurrentState() != LearningState.FAILED_TERMINATED) {
            session.setExpectedState(flow.getCurrentState());
            simulateLearnerAnswer("{\"evaluation\": \"PASS\"}", true);
        }
        assertEquals(LearningState.TRANSFER, flow.getCurrentState());
        
        session.setExpectedState(LearningState.TRANSFER);
        simulateLearnerAnswer("{\"evaluation\": \"PASS\", \"feedback\": \"Transfer solved.\"}", true);
        
        // With current basic transfer policy, as long as it isn't an explicit duplicate, it passes.
        assertEquals(LearningState.COMPLETED, flow.getCurrentState());
    }
    
    @Test
    void test7_PartialAnswerOrHintRequests() {
        // Learner asks for a hint. LLM correctly assesses it as HINT.
        simulateLearnerAnswer("{\"evaluation\": \"HINT\", \"feedback\": \"Try a loop.\"}", false);
        
        // State does not advance, but it is recorded safely.
        assertEquals(LearningState.CONCEPT_ASSESSMENT, flow.getCurrentState());
    }
    
    @Test
    void test8_InvalidAiResponseSafety() {
        // AI returns junk JSON
        simulateLearnerAnswer("{junk}", false);
        
        // State remains protected
        assertEquals(LearningState.CONCEPT_ASSESSMENT, flow.getCurrentState());
    }
}
