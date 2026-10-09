package com.javadsamastery.ai.service.impl;

import com.javadsamastery.ai.dto.MisconceptionDto;
import com.javadsamastery.ai.provider.AIProviderException;
import com.javadsamastery.ai.provider.LLMProvider;
import com.javadsamastery.ai.service.MisconceptionDetector;

public class LLMMisconceptionDetector implements MisconceptionDetector {
    private final LLMProvider provider;

    public LLMMisconceptionDetector(LLMProvider provider) {
        this.provider = provider;
    }

    @Override
    public MisconceptionDto detect(String learnerAnswer, String topicContext) {
        String systemPrompt = "Detect common DSA misconceptions in the learner's answer.";
        String userPrompt = "Context: " + topicContext + "\nAnswer: " + learnerAnswer;
        
        try {
            MisconceptionDto result = provider.generateStructured(systemPrompt, userPrompt, MisconceptionDto.class);
            if (result == null) {
                return new MisconceptionDto(false, null, null);
            }
            return result;
        } catch (AIProviderException e) {
            return new MisconceptionDto(false, "Detection failed safely.", null);
        }
    }
}
