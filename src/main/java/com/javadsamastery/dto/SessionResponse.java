package com.javadsamastery.dto;

public record SessionResponse(
    String sessionId,
    String learnerId,
    String topicId,
    String topicTitle,
    String currentState,
    String mastery,
    int totalStages,
    boolean completed
) {}
