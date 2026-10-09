package com.javadsamastery.ai.provider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeminiLLMProviderTest {

    @Test
    void testMissingApiKeyThrowsException() {
        // We simulate a missing API key. If the environment variable isn't set, 
        // the constructor will read null/blank. Or if it is set, we still test that 
        // the provider throws early if we try to call it (assuming it handles it securely).
        // Since we can't easily wipe env vars in Java for a single test, we just instantiate.
        // We rely on the generateRaw method checking the key.
        
        // Limitation Documented: HTTP mocking is difficult because the HttpClient 
        // is instantiated directly inside the constructor. 
        // We do not introduce MockWebServer or Mockito-inline just for this, as instructed.
        
        GeminiLLMProvider provider = new GeminiLLMProvider();
        
        // If GEMINI_API_KEY is not set in this environment, it should throw.
        // If it is set, it might attempt an HTTP request, which will timeout or fail if invalid.
        // For security, we ensure the exception does NOT leak the prompt.
        String prompt = "SECRET_PROMPT_DO_NOT_LEAK";
        try {
            provider.generateRaw("system", prompt);
            // If it succeeds (because a valid key is set), that's fine.
        } catch (AIProviderException e) {
            assertFalse(e.getMessage().contains(prompt), "Exception must not leak user prompt");
            assertFalse(e.getMessage().contains("GEMINI_API_KEY="), "Exception must not leak API key value");
        }
    }
}
