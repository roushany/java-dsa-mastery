package com.javadsamastery.engine;

import com.javadsamastery.domain.content.QuestionDefinition;

public record QuestionSelectionResult(
    QuestionDefinition selectedQuestion,
    String explanation
) {}
