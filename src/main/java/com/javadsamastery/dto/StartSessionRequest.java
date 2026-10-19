package com.javadsamastery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StartSessionRequest(
    @NotBlank(message = "topicId must not be blank")
    @Size(max = 64, message = "topicId must not exceed 64 characters")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "topicId must contain only lowercase letters, digits, and hyphens")
    String topicId,

    @Size(max = 64, message = "learnerId must not exceed 64 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "learnerId must contain only alphanumeric characters, underscores, and hyphens")
    String learnerId
) {}
