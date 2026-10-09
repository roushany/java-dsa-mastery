package com.javadsamastery.ai.mock;

import com.javadsamastery.ai.provider.AIProviderException;
import com.javadsamastery.ai.provider.LLMProvider;
import com.javadsamastery.ai.dto.EvaluationResultDto;
import com.javadsamastery.ai.dto.GeneratedQuestion;
import com.javadsamastery.ai.dto.HintDto;
import com.javadsamastery.ai.dto.MisconceptionDto;

import java.util.HashMap;
import java.util.Map;

/**
 * A deterministic mock provider for testing. Returns predefined responses based on type.
 */
public class MockLLMProvider implements LLMProvider {

    private final Map<Class<?>, Object> cannedResponses = new HashMap<>();
    private boolean simulateFailure = false;

    public <T> void setCannedResponse(Class<T> clazz, T response) {
        cannedResponses.put(clazz, response);
    }

    public void setSimulateFailure(boolean simulateFailure) {
        this.simulateFailure = simulateFailure;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T generateStructured(String systemPrompt, String userPrompt, Class<T> responseType) throws AIProviderException {
        if (simulateFailure) {
            throw new AIProviderException("Simulated provider failure");
        }
        
        if (cannedResponses.containsKey(responseType)) {
            return (T) cannedResponses.get(responseType);
        }

        // To support the deterministic test suite which expects generateRaw behavior:
        if (responseType == com.javadsamastery.ai.dto.EvaluationResultDto.class) {
            String raw = generateRaw(systemPrompt, userPrompt);
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                return mapper.readValue(raw, responseType);
            } catch (Exception e) {
                throw new AIProviderException("Mock failed to parse JSON: " + e.getMessage());
            }
        }

        if (responseType == GeneratedQuestion.class) {
            return (T) new GeneratedQuestion("Mock Question?", "Mock Concept", "EASY");
        }
        if (responseType == HintDto.class) {
            return (T) new HintDto("Mock Hint", false);
        }
        if (responseType == MisconceptionDto.class) {
            return (T) new MisconceptionDto(false, null, null);
        }

        throw new AIProviderException("No canned response available for type: " + responseType.getName());
    }

    @Override
    public String generateRaw(String systemPrompt, String userPrompt) throws AIProviderException {
        if (simulateFailure) {
            throw new AIProviderException("Simulated provider failure");
        }
        if (cannedResponses.containsKey(String.class)) {
            return (String) cannedResponses.get(String.class);
        }
        return "{\"evaluation\": \"PASS\", \"feedback\": \"Mock Feedback\"}";
    }
}
