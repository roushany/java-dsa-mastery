package com.javadsamastery.ai.dto;

import com.javadsamastery.domain.evidence.EvaluationResult;

public record AIAssessment(
    String attemptId,
    String topicId,
    String questionId,
    EvaluationResult evaluation,
    String feedback,
    String misconceptionCategory
) {}
