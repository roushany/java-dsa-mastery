package com.javadsamastery.ai.service.impl;

import com.javadsamastery.ai.dto.HintDto;
import com.javadsamastery.ai.provider.AIProviderException;
import com.javadsamastery.ai.provider.LLMProvider;
import com.javadsamastery.ai.service.HintGenerator;

public class LLMHintGenerator implements HintGenerator {
    private final LLMProvider provider;

    public LLMHintGenerator(LLMProvider provider) {
        this.provider = provider;
    }

    @Override
    public HintDto generateHint(String problemId, String learnerContext, int currentHintLevel) {
        String systemPrompt = "Generate a progressive hint based on the level. Higher levels give more direct guidance.";
        String userPrompt = "Problem: " + problemId + "\nContext: " + learnerContext + "\nLevel: " + currentHintLevel;
        
        try {
            HintDto hint = provider.generateStructured(systemPrompt, userPrompt, HintDto.class);
            if (hint == null || hint.hintText() == null) {
                return new HintDto("Generic hint: please review the prerequisites.", true);
            }
            return hint;
        } catch (AIProviderException e) {
            return new HintDto("Hint generation unavailable.", true);
        }
    }
}
