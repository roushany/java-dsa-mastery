package com.javadsamastery.ai.service.impl;

import com.javadsamastery.ai.dto.GeneratedQuestion;
import com.javadsamastery.ai.provider.AIProviderException;
import com.javadsamastery.ai.provider.LLMProvider;
import com.javadsamastery.ai.service.QuestionGenerator;

public class LLMQuestionGenerator implements QuestionGenerator {
    private final LLMProvider provider;

    public LLMQuestionGenerator(LLMProvider provider) {
        this.provider = provider;
    }

    @Override
    public GeneratedQuestion generateQuestion(String topicId, String difficulty) {
        String systemPrompt = "Generate a novel DSA question.";
        String userPrompt = "Topic: " + topicId + "\nDifficulty: " + difficulty;
        
        try {
            GeneratedQuestion q = provider.generateStructured(systemPrompt, userPrompt, GeneratedQuestion.class);
            if (q == null || q.questionText() == null) {
                throw new AIProviderException("Null or missing fields in question generation.");
            }
            return q;
        } catch (AIProviderException e) {
            return new GeneratedQuestion("Fallback Question: Implement " + topicId, "Basic " + topicId, difficulty);
        }
    }
}
