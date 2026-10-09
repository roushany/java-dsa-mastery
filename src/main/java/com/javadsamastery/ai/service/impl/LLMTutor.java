package com.javadsamastery.ai.service.impl;

import com.javadsamastery.ai.provider.AIProviderException;
import com.javadsamastery.ai.provider.LLMProvider;
import com.javadsamastery.ai.service.Tutor;

public class LLMTutor implements Tutor {
    private final LLMProvider provider;

    public LLMTutor(LLMProvider provider) {
        this.provider = provider;
    }

    @Override
    public String explainConcept(String topicId, String learnerContext) {
        String systemPrompt = "Explain a concept pedagogically without giving away the direct answer. Return a plain string.";
        String userPrompt = "Topic: " + topicId + "\nContext: " + learnerContext;
        
        try {
            String explanation = provider.generateStructured(systemPrompt, userPrompt, String.class);
            if (explanation == null || explanation.isBlank()) {
                return "Please review the fundamentals of " + topicId + ".";
            }
            return explanation;
        } catch (AIProviderException e) {
            return "Tutor service temporarily unavailable.";
        }
    }
}
