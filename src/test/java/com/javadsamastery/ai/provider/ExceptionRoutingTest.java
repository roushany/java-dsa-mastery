package com.javadsamastery.ai.provider;
import com.javadsamastery.ai.service.impl.LLMAnswerEvaluator;
import com.javadsamastery.ai.dto.EvaluationResultDto;
import com.javadsamastery.engine.AITutorEngine;
import com.javadsamastery.ai.dto.AIAssessment;
import com.javadsamastery.domain.evidence.EvaluationResult;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ExceptionRoutingTest {
    @Test
    public void testHttp503RemainsProviderFailure() {
        LLMProvider mock = new LLMProvider() {
            public <T> T generateStructured(String s, String u, Class<T> r) throws AIProviderException {
                throw new AIProviderException("PROVIDER_FAILURE: Gemini API server error (HTTP 503).");
            }
            public String generateRaw(String s, String u) throws AIProviderException { return ""; }
            public String getModelName() { return "mock"; }
        };
        AITutorEngine engine = new AITutorEngine(null, null, new LLMAnswerEvaluator(mock));
        AIAssessment assess = engine.assessAnswer("a", "t", "q", "txt", "exp", "ans", null);
        assertEquals(EvaluationResult.PROVIDER_FAILURE, assess.evaluation());
    }

    @Test
    public void testTimeoutRemainsProviderTimeout() {
        LLMProvider mock = new LLMProvider() {
            public <T> T generateStructured(String s, String u, Class<T> r) throws AIProviderException {
                throw new AIProviderException("PROVIDER_TIMEOUT: Gemini API request timed out after 30 seconds.", new java.net.http.HttpTimeoutException("timeout"));
            }
            public String generateRaw(String s, String u) throws AIProviderException { return ""; }
            public String getModelName() { return "mock"; }
        };
        AITutorEngine engine = new AITutorEngine(null, null, new LLMAnswerEvaluator(mock));
        AIAssessment assess = engine.assessAnswer("a", "t", "q", "txt", "exp", "ans", null);
        assertEquals(EvaluationResult.PROVIDER_TIMEOUT, assess.evaluation());
    }

    @Test
    public void testValidPartialRemainsPartial() {
        LLMProvider mock = new LLMProvider() {
            public <T> T generateStructured(String s, String u, Class<T> r) throws AIProviderException {
                return (T) new EvaluationResultDto("PARTIAL", 0, "none", "ok", "none");
            }
            public String generateRaw(String s, String u) throws AIProviderException { return ""; }
            public String getModelName() { return "mock"; }
        };
        AITutorEngine engine = new AITutorEngine(null, null, new LLMAnswerEvaluator(mock));
        AIAssessment assess = engine.assessAnswer("a", "t", "q", "txt", "exp", "ans", null);
        assertEquals(EvaluationResult.PARTIAL, assess.evaluation());
    }

    @Test
    public void testValidInvalidResponseRemainsInvalidResponse() {
        LLMProvider mock = new LLMProvider() {
            public <T> T generateStructured(String s, String u, Class<T> r) throws AIProviderException {
                return (T) new EvaluationResultDto("INVALID_RESPONSE", 0, "none", "ok", "none");
            }
            public String generateRaw(String s, String u) throws AIProviderException { return ""; }
            public String getModelName() { return "mock"; }
        };
        AITutorEngine engine = new AITutorEngine(null, null, new LLMAnswerEvaluator(mock));
        AIAssessment assess = engine.assessAnswer("a", "t", "q", "txt", "exp", "ans", null);
        assertEquals(EvaluationResult.INVALID_RESPONSE, assess.evaluation());
    }
}
