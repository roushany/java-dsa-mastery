package com.javadsamastery.dto;

public record QuestionResponse(
    String attemptId,
    String sessionId,
    String topicId,
    String stage,
    String difficulty,
    String content,
    int hintsUsed,
    boolean isTerminal
) {}
