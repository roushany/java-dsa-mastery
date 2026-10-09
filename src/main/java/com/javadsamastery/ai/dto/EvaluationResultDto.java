package com.javadsamastery.ai.dto;

public record EvaluationResultDto(
    String evaluation,
    int confidenceScore,
    String misconceptionCategory,
    String feedback,
    String recommendedRemediation
) {}
