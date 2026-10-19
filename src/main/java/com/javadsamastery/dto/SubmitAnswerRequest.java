package com.javadsamastery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitAnswerRequest(
    @NotBlank(message = "attemptId must not be blank")
    @Size(max = 64, message = "attemptId must not exceed 64 characters")
    String attemptId,

    @NotBlank(message = "answer must not be blank")
    @Size(min = 1, max = 2000, message = "answer must not exceed 2000 characters")
    String answer,

    @Deprecated(since = "Phase 1.2", forRemoval = true)
    Boolean codeTestsPassed // Deprecated: untrusted client data, ignored by server
) {}
