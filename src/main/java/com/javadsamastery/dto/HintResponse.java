package com.javadsamastery.dto;

public record HintResponse(
    String attemptId,
    int hintLevel,
    String hintText
) {}
