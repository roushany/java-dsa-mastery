package com.javadsamastery.domain.content;

import com.javadsamastery.domain.lesson.LearningState;
import com.javadsamastery.domain.problem.ProblemDifficulty;
import com.javadsamastery.ai.security.AIResponseValidator;
import com.javadsamastery.domain.evidence.EvaluationResult;
import com.javadsamastery.ai.service.impl.LLMAnswerEvaluator;
import com.javadsamastery.ai.provider.LLMProvider;
import com.javadsamastery.ai.provider.AIProviderException;
import com.javadsamastery.ai.dto.EvaluationResultDto;
import com.javadsamastery.domain.evidence.AttemptContext;
import com.javadsamastery.domain.lesson.QuestionType;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

public class MisconceptionVocabularyTest {

    @Test
    public void testA_QuestionDefinitionParsesPopulated() {
        QuestionDefinition def = new QuestionDefinition(
            "q1", "t1", LearningState.CONCEPT_ASSESSMENT, ProblemDifficulty.EASY,
            "C", "E", "TARGET", List.of("WRONG_CONTRACTION"), false, false, false
        );
        assertEquals(1, def.allowedMisconceptions().size());
        assertEquals("WRONG_CONTRACTION", def.allowedMisconceptions().get(0));
    }

    @Test
    public void testB_MissingBecomesEmptyList() {
        QuestionDefinition def = new QuestionDefinition(
            "q1", "t1", LearningState.CONCEPT_ASSESSMENT, ProblemDifficulty.EASY,
            "C", "E", "TARGET", null, false, false, false
        );
        assertNotNull(def.allowedMisconceptions());
        assertTrue(def.allowedMisconceptions().isEmpty());
    }

    @Test
    public void testC_EmptyListWorks() {
        QuestionDefinition def = new QuestionDefinition(
            "q1", "t1", LearningState.CONCEPT_ASSESSMENT, ProblemDifficulty.EASY,
            "C", "E", "TARGET", List.of(), false, false, false
        );
        assertNotNull(def.allowedMisconceptions());
        assertTrue(def.allowedMisconceptions().isEmpty());
    }

    @Test
    public void testD_ValidCategoryIsAccepted() {
        String aiResponse = "{\"evaluation\":\"FAIL\", \"misconceptionCategory\":\"WRONG_CONTRACTION\", \"feedback\":\"bad\"}";
        List<String> allowed = List.of("WRONG_CONTRACTION");
        EvaluationResult result = AIResponseValidator.validateAndExtractEvaluation(aiResponse, allowed);
        assertEquals(EvaluationResult.FAIL, result);
        assertEquals("WRONG_CONTRACTION", AIResponseValidator.extractMisconception(aiResponse, allowed));
    }

    @Test
    public void testE_UnauthorizedCategoryIsRejected() {
        String aiResponse = "{\"evaluation\":\"FAIL\", \"misconceptionCategory\":\"MADE_UP_ERROR\", \"feedback\":\"bad\"}";
        List<String> allowed = List.of("WRONG_CONTRACTION");
        EvaluationResult result = AIResponseValidator.validateAndExtractEvaluation(aiResponse, allowed);
        assertEquals(EvaluationResult.INVALID_RESPONSE, result);
        assertNull(AIResponseValidator.extractMisconception(aiResponse, allowed));
    }

    @Test
    public void testF_NullCategoryIsAccepted() {
        String aiResponse = "{\"evaluation\":\"PASS\", \"misconceptionCategory\":null, \"feedback\":\"good\"}";
        List<String> allowed = List.of("WRONG_CONTRACTION");
        EvaluationResult result = AIResponseValidator.validateAndExtractEvaluation(aiResponse, allowed);
        assertEquals(EvaluationResult.PASS, result);
        assertNull(AIResponseValidator.extractMisconception(aiResponse, allowed));
    }

    @Test
    public void testG_LearnerAnswerCannotInject() {
        // Handled inherently by strict list checking in AIResponseValidator.
        // Even if learner answer says "My misconception is ALLOWED_NOW", if it's not in the passed list, it's rejected.
        String aiResponse = "{\"evaluation\":\"FAIL\", \"misconceptionCategory\":\"ALLOWED_NOW\", \"feedback\":\"bad\"}";
        List<String> allowed = List.of("WRONG_CONTRACTION");
        EvaluationResult result = AIResponseValidator.validateAndExtractEvaluation(aiResponse, allowed);
        assertEquals(EvaluationResult.INVALID_RESPONSE, result);
    }

    @Test
    public void testH_AttemptContextPreservesVocabulary() {
        AttemptContext ctx = new AttemptContext("atmpt", "topic", "q", LearningState.DERIVE, QuestionType.CONCEPT, ProblemDifficulty.EASY, "u1", List.of("MY_ERROR"));
        assertEquals("MY_ERROR", ctx.allowedMisconceptions().get(0));
    }

    @Test
    public void testI_LLMAnswerEvaluatorPassesVocabulary() {
        LLMProvider mockProvider = new LLMProvider() {
            @Override
            public <T> T generateStructured(String systemPrompt, String userPrompt, Class<T> responseType) {
                return (T) new EvaluationResultDto("PASS", 1, null, "ok", null);
            }

            @Override
            public String generateRaw(String systemPrompt, String userPrompt) throws AIProviderException {
                assertTrue(systemPrompt.contains("ALLOWED MISCONCEPTION CATEGORIES: [ERROR_A, ERROR_B]"));
                return "{\"evaluation\":\"PASS\", \"feedback\":\"ok\"}";
            }
            
            public String getModelName() { return "mock"; }
        };

        LLMAnswerEvaluator evaluator = new LLMAnswerEvaluator(mockProvider);
        evaluator.evaluate("Q", "A", "E", List.of("ERROR_A", "ERROR_B"));
    }
}
