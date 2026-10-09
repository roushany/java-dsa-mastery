package com.javadsamastery.domain.content;

import com.javadsamastery.domain.lesson.LearningState;
import com.javadsamastery.domain.problem.ProblemDifficulty;

import java.util.List;

/**
 * Data-driven definition of a generic question, loaded from a question bank.
 */
public record QuestionDefinition(
    String questionId,
    String topicId,
    LearningState targetStage,
    ProblemDifficulty difficulty,
    String content,
    String expectedEvidence,
    String targetsMisconception,
    List<String> allowedMisconceptions,
    boolean isRemediation,
    boolean isTransfer,
    boolean isRetrieval
) {
    public QuestionDefinition {
        if (questionId == null || questionId.isBlank()) throw new IllegalArgumentException("questionId required");
        if (topicId == null || topicId.isBlank()) throw new IllegalArgumentException("topicId required");
        if (targetStage == null) throw new IllegalArgumentException("targetStage required");
        if (difficulty == null) throw new IllegalArgumentException("difficulty required");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content required");
        if (expectedEvidence == null || expectedEvidence.isBlank()) throw new IllegalArgumentException("expectedEvidence required");
        allowedMisconceptions = allowedMisconceptions != null ? List.copyOf(allowedMisconceptions) : List.of();
    }
}
