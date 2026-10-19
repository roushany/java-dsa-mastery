package com.javadsamastery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestHintRequest(
    @NotBlank(message = "attemptId must not be blank")
    @Size(max = 64, message = "attemptId must not exceed 64 characters")
    String attemptId
) {}
