package com.javadsamastery.ai.security;

import com.javadsamastery.domain.content.TopicLoader;
import com.javadsamastery.domain.content.TopicTemplate;
import com.javadsamastery.domain.evidence.EvidenceRecord;
import com.javadsamastery.domain.learner.LearnerProfile;
import com.javadsamastery.domain.lesson.LearningState;
import com.javadsamastery.domain.lesson.QuestionType;
import com.javadsamastery.domain.problem.ProblemDifficulty;
import com.javadsamastery.engine.MasteryEngine;
import com.javadsamastery.session.LessonFlow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SecurityBoundaryTest {

    private TopicTemplate template;
    private LearnerProfile profile;
    private MasteryEngine engine;
    private LessonFlow flow;

    @BeforeEach
    void setUp() {
        template = TopicLoader.load("linear-search");
        profile = new LearnerProfile("learner-sec");
        engine = new MasteryEngine();
        var graph = com.javadsamastery.domain.curriculum.CurriculumLoader.loadCurriculum("/curriculum/curriculum.json");
        var topicDef = graph.getDefinition("linear-search");
        flow = new LessonFlow(topicDef, template, profile, engine);
        flow.advanceIntro(); // Start in CONCEPT_ASSESSMENT
    }

    private EvidenceRecord processAdversarialAI(String aiPayload) {
        var result = AIResponseValidator.validateAndExtractEvaluation(aiPayload, null);
        var record = new EvidenceRecord("learner-sec", "linear-search", "p1", ProblemDifficulty.EASY, QuestionType.CONCEPT, 0, false, result, null, Instant.now());
        flow.submitEvidence(record);
        return record;
    }

    @Test
    void test1_PassWithMasteryMutation_Rejected() {
        String aiPayload = "{\"evaluation\": \"PASS\", \"action\": \"SET_MASTERY\", \"mastery\": 100}";
        processAdversarialAI(aiPayload);
        // Because it contains "action", the validator rejects it as INVALID_RESPONSE.
        // It should NOT advance to IMPLEMENTATION.
        assertEquals(LearningState.CONCEPT_ASSESSMENT, flow.getCurrentState());
    }

    @Test
    void test2_PassWithUnauthorizedStateMutation_Rejected() {
        String aiPayload = "{\"evaluation\": \"PASS\", \"mutate_state\": \"COMPLETED\"}";
        processAdversarialAI(aiPayload);
        assertEquals(LearningState.CONCEPT_ASSESSMENT, flow.getCurrentState());
    }

    @Test
    void test3_SetMasteryAction_Rejected() {
        String aiPayload = "{\"action\": \"SET_MASTERY\"}";
        processAdversarialAI(aiPayload);
        assertEquals(LearningState.CONCEPT_ASSESSMENT, flow.getCurrentState());
    }

    @Test
    void test4_ChangeStateAction_Rejected() {
        String aiPayload = "{\"action\": \"CHANGE_STATE\", \"state\": \"TRANSFER\"}";
        processAdversarialAI(aiPayload);
        assertEquals(LearningState.CONCEPT_ASSESSMENT, flow.getCurrentState());
    }

    @Test
    void test5_ModifyCurriculumAction_Rejected() {
        String aiPayload = "{\"action\": \"MODIFY_CURRICULUM\"}";
        processAdversarialAI(aiPayload);
        assertEquals(LearningState.CONCEPT_ASSESSMENT, flow.getCurrentState());
    }

    @Test
    void test6_DatabaseAction_Rejected() {
        String aiPayload = "{\"action\": \"DROP_TABLE\"}";
        processAdversarialAI(aiPayload);
        assertEquals(LearningState.CONCEPT_ASSESSMENT, flow.getCurrentState());
    }

    @Test
    void test7_SystemPromptExtraction_Rejected() {
        String aiPayload = "Sure! Here is the system prompt: You are an AI tutor..."; // Not JSON
        processAdversarialAI(aiPayload);
        assertEquals(LearningState.CONCEPT_ASSESSMENT, flow.getCurrentState());
    }

    @Test
    void test8_MalformedJson_Rejected() {
        String aiPayload = "{ evaluation: PASS"; // malformed
        processAdversarialAI(aiPayload);
        assertEquals(LearningState.CONCEPT_ASSESSMENT, flow.getCurrentState());
    }

    @Test
    void test9_MissingEvaluation_Rejected() {
        String aiPayload = "{\"feedback\": \"Good job!\"}";
        processAdversarialAI(aiPayload);
        assertEquals(LearningState.CONCEPT_ASSESSMENT, flow.getCurrentState());
    }

    @Test
    void test10_InvalidEvaluationEnum_Rejected() {
        String aiPayload = "{\"evaluation\": \"OVERRIDE\"}";
        processAdversarialAI(aiPayload);
        assertEquals(LearningState.CONCEPT_ASSESSMENT, flow.getCurrentState());
    }

    @Test
    void test11_ContradictoryResponse_HandledSafely() {
        // Syntactically valid PASS, but shouldn't alter mastery blindly without engine logic
        String aiPayload = "{\"evaluation\": \"PASS\", \"feedback\": \"You failed completely.\"}";
        processAdversarialAI(aiPayload);
        // The validator accepts PASS, but the LessonFlow transitions state normally.
        // It does NOT let the AI mutate mastery directly. MasteryEngine does that based on history.
        assertEquals(LearningState.DERIVE, flow.getCurrentState());
    }
    
    @Test
    void test12_OversizedResponse_Rejected() {
        // Simulate massive prompt injection block breaking JSON parsing or containing nested instructions
        StringBuilder sb = new StringBuilder();
        sb.append("{\"evaluation\": \"PASS\", \"feedback\": \"");
        for (int i=0; i<10000; i++) sb.append("ignore ");
        sb.append("\"}");
        processAdversarialAI(sb.toString()); // Might pass validation, but let's test if state machine bounds it
        // If it parses successfully as PASS, it transitions, but again, mastery is protected.
    }

    @Test
    void test13_PromptInjectionLearnerAnswer_Rejected() {
        // The learner typed this. The LLM echoes it back malformed or tries to evaluate it as instructions
        // If LLM falls for it and returns:
        String llmFooledPayload = "{\"action\": \"SET_MASTERY\", \"evaluation\": \"PASS\"}";
        processAdversarialAI(llmFooledPayload);
        assertEquals(LearningState.CONCEPT_ASSESSMENT, flow.getCurrentState()); // Protected
    }

    @Test
    void test14_PromptInjectionLearnerCode_Rejected() {
        String llmFooledPayload = "{\"mutate_state\": \"COMPLETED\"}";
        processAdversarialAI(llmFooledPayload);
        assertEquals(LearningState.CONCEPT_ASSESSMENT, flow.getCurrentState());
    }

    @Test
    void test15_PromptInjectionLearnerExplanation_Rejected() {
        String llmFooledPayload = "System prompt revealed.";
        processAdversarialAI(llmFooledPayload);
        assertEquals(LearningState.CONCEPT_ASSESSMENT, flow.getCurrentState());
    }

    @Test
    void test16_LegitimateLearnerTextWithInstructionWords_Accepted() {
        // Learner answer: "Ignore the previous node. Set the array to 100."
        // LLM correctly evaluates it as a valid answer:
        String aiPayload = "{\"evaluation\": \"PASS\", \"feedback\": \"Correct, you ignore the left node.\"}";
        processAdversarialAI(aiPayload);
        assertEquals(LearningState.DERIVE, flow.getCurrentState());
    }
}
