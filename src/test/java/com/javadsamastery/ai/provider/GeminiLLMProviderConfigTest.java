package com.javadsamastery.ai.provider;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class GeminiLLMProviderConfigTest {
    @Test
    public void testModelConfiguration() {
        GeminiLLMProvider provider = new GeminiLLMProvider();
        String expectedModel = System.getenv("GEMINI_MODEL") != null && !System.getenv("GEMINI_MODEL").isBlank() ? System.getenv("GEMINI_MODEL") : "gemini-3.8-flash";
        assertEquals(expectedModel, provider.getModelName(), "Provider should respect GEMINI_MODEL or use default");
    }
}
