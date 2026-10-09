package com.javadsamastery.domain.evidence;

import com.javadsamastery.domain.lesson.LearningState;
import com.javadsamastery.domain.lesson.QuestionType;
import com.javadsamastery.domain.problem.ProblemDifficulty;
import java.util.List;

public record AttemptContext(
    String attemptId,
    String topicId,
    String questionId,
    LearningState expectedState,
    QuestionType questionType,
    ProblemDifficulty difficulty,
    String learnerId,
    List<String> allowedMisconceptions
) {}
