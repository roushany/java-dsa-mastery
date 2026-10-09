package com.javadsamastery.domain.evidence;

import com.javadsamastery.domain.problem.ProblemDifficulty;
import com.javadsamastery.domain.lesson.QuestionType;
import java.time.Instant;

/**
 * Immutable record of a single learner interaction/evaluation.
 * Retains auditable history of exactly how a problem was solved.
 */
public record EvidenceRecord(
    String learnerId,
    String topicId,
    String problemId,
    ProblemDifficulty difficulty,
    QuestionType questionType,
    int hintCount,
    boolean isRetrievalPractice,
    EvaluationResult result,
    String misconceptionCategory,
    Instant timestamp
) {
    public EvidenceRecord {
        if (learnerId == null || learnerId.isBlank()) throw new IllegalArgumentException("Learner ID cannot be blank");
        if (topicId == null || topicId.isBlank()) throw new IllegalArgumentException("Topic ID cannot be blank");
        if (problemId == null || problemId.isBlank()) throw new IllegalArgumentException("Problem ID cannot be blank");
        if (difficulty == null) throw new IllegalArgumentException("Difficulty cannot be null");
        if (questionType == null) throw new IllegalArgumentException("QuestionType cannot be null");
        if (hintCount < 0) throw new IllegalArgumentException("Hint count cannot be negative");
        if (result == null) throw new IllegalArgumentException("Result cannot be null");
        if (timestamp == null) timestamp = Instant.now();
    }
}
