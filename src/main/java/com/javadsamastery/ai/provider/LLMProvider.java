package com.javadsamastery.ai.provider;

/**
 * Provider-independent interface for external AI services.
 * No specific AI SDKs (like OpenAI or Gemini SDK) should leak past this interface.
 */
public interface LLMProvider {
    /**
     * Generates a structured response mapped to a specific Java Class.
     * @param systemPrompt Instructions for the LLM.
     * @param userPrompt The user's input/context.
     * @param responseType The Class to map the JSON response to.
     * @return An instance of T.
     * @throws AIProviderException if the provider fails or returns malformed data.
     */
    <T> T generateStructured(String systemPrompt, String userPrompt, Class<T> responseType) throws AIProviderException;
    String generateRaw(String systemPrompt, String userPrompt) throws AIProviderException;
}
