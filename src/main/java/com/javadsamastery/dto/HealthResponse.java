package com.javadsamastery.dto;

public record HealthResponse(
    String status,
    String version,
    int activeTopics,
    boolean aiConfigured
) {}
