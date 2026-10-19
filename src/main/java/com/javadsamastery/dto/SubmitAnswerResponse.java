package com.javadsamastery.dto;

public record SubmitAnswerResponse(
    String attemptId,
    String evaluation,
    String feedback,
    String misconceptionDetected,
    String currentState,
    String currentMastery,
    boolean stageAdvanced,
    int hintsUsed
) {}
