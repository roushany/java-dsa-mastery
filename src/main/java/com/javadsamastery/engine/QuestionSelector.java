package com.javadsamastery.engine;

import com.javadsamastery.domain.content.QuestionBank;
import com.javadsamastery.domain.content.QuestionDefinition;
import com.javadsamastery.domain.learner.TopicState;
import com.javadsamastery.domain.lesson.LearningState;
import com.javadsamastery.domain.problem.ProblemDifficulty;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Deterministic selector that picks the best next question 
 * given the learner's state, current objective, and difficulty policy.
 */
public class QuestionSelector {
    
    private final QuestionBank questionBank;

    public QuestionSelector(QuestionBank questionBank) {
        this.questionBank = questionBank;
    }

    public java.util.Set<String> getValidMisconceptions(String topicId) {
        return questionBank.getQuestions().stream()
                .filter(q -> q.topicId().equals(topicId))
                .map(QuestionDefinition::targetsMisconception)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
    }

    public QuestionSelectionResult selectNextQuestion(TopicState state, LearningState currentObjective) {
        // 1. Difficulty Adaptation
        ProblemDifficulty targetDifficulty = determineTargetDifficulty(state, currentObjective);
        
        // 2. Fetch candidates matching topic & objective
        List<QuestionDefinition> allForObjective = questionBank.getQuestions().stream()
                .filter(q -> q.topicId().equals(state.getTopicId()) && q.targetStage() == currentObjective)
                .toList();

        if (allForObjective.isEmpty()) {
            throw new IllegalStateException("No questions available for objective: " + currentObjective + " in topic " + state.getTopicId());
        }

        // 3. Misconception targeting (Highest priority if active)
        Optional<QuestionDefinition> misconceptionTarget = allForObjective.stream()
                .filter(q -> q.targetsMisconception() != null && state.getActiveMisconceptions().contains(q.targetsMisconception()))
                .findFirst();

        if (misconceptionTarget.isPresent()) {
            return new QuestionSelectionResult(misconceptionTarget.get(), 
                "Selected to address active misconception: " + misconceptionTarget.get().targetsMisconception());
        }

        // 4. Remediation priority
        if (state.getConsecutiveFailures() > 1 && currentObjective != LearningState.REMEDIATION) {
            Optional<QuestionDefinition> remediationQuestion = allForObjective.stream()
                .filter(QuestionDefinition::isRemediation)
                .findFirst();
            if (remediationQuestion.isPresent()) {
                return new QuestionSelectionResult(remediationQuestion.get(),
                    "Selected remediation due to repeated failures (" + state.getConsecutiveFailures() + ").");
            }
        }

        // 5. Select based on difficulty
        List<QuestionDefinition> difficultyMatches = allForObjective.stream()
                .filter(q -> q.difficulty() == targetDifficulty)
                .toList();

        QuestionDefinition selected;
        String reason;
        if (!difficultyMatches.isEmpty()) {
            selected = difficultyMatches.get(0); // Deterministic pick
            reason = "Selected standard progression at difficulty: " + targetDifficulty;
        } else {
            // Fallback to nearest difficulty
            selected = allForObjective.get(0);
            reason = "Fallback: exact difficulty match not found.";
        }
        
        return new QuestionSelectionResult(selected, reason);
    }

    private ProblemDifficulty determineTargetDifficulty(TopicState state, LearningState objective) {
        // Simple deterministic policy
        if (state.getConsecutiveFailures() > 1) {
            return ProblemDifficulty.EASY;
        }
        
        // If they're strong in this area, increase difficulty
        if (objective == LearningState.DERIVE && state.isDerivationStrong()) {
            return ProblemDifficulty.HARD;
        }
        if (objective == LearningState.IMPLEMENTATION && state.isImplementationStrong()) {
            return ProblemDifficulty.HARD;
        }
        if (objective == LearningState.TRANSFER && state.isTransferStrong()) {
            return ProblemDifficulty.HARD;
        }
        
        return ProblemDifficulty.MEDIUM;
    }
}
