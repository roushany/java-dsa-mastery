package com.javadsamastery.ai.provider;

import com.javadsamastery.ai.service.impl.LLMAnswerEvaluator;
import com.javadsamastery.ai.dto.EvaluationResultDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class GeminiHttp404SimulationTest {
    @Test
    public void testExceptionRouting() {
        LLMProvider mockProvider = new LLMProvider() {
            @Override
            public String generateRaw(String systemPrompt, String userPrompt) throws AIProviderException {
                throw new AIProviderException("PROVIDER_FAILURE: Gemini API client error (HTTP 404).");
            }

            @Override
            public <T> T generateStructured(String systemPrompt, String userPrompt, Class<T> responseType) throws AIProviderException {
                throw new AIProviderException("PROVIDER_FAILURE: Gemini API client error (HTTP 404).");
            }
        };

        LLMAnswerEvaluator evaluator = new LLMAnswerEvaluator(mockProvider);
        assertThrows(AIProviderException.class, () -> evaluator.evaluate("Q", "A", "E", null));
    }
}
