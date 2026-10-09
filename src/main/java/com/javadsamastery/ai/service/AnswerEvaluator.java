package com.javadsamastery.ai.service;

import com.javadsamastery.ai.dto.EvaluationResultDto;

import java.util.List;

public interface AnswerEvaluator {
    EvaluationResultDto evaluate(String question, String answer, String expectedConcept, List<String> allowedMisconceptions) throws com.javadsamastery.ai.provider.AIProviderException;
}
