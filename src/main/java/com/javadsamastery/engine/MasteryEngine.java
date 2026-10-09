package com.javadsamastery.engine;

import com.javadsamastery.domain.evidence.EvaluationResult;
import com.javadsamastery.domain.evidence.EvidenceRecord;
import com.javadsamastery.domain.learner.LearnerProfile;
import com.javadsamastery.domain.lesson.QuestionType;
import com.javadsamastery.domain.topic.TopicMastery;

import java.util.List;
import java.util.Comparator;

/**
 * Deterministic rules engine to evaluate mastery from auditable evidence.
 */
public class MasteryEngine {

    public void evaluateMastery(LearnerProfile profile, String topicId, List<EvidenceRecord> history) {
        List<EvidenceRecord> topicHistory = history.stream()
                .filter(e -> e.topicId().equals(topicId) && e.learnerId().equals(profile.getLearnerId()))
                .sorted(Comparator.comparing(EvidenceRecord::timestamp))
                .toList();

        profile.getTopicState(topicId).evaluateFromHistory(topicHistory);
    }

    private boolean isConceptQuestion(QuestionType type) {
        return type == QuestionType.CONCEPT || type == QuestionType.INTUITION || 
               type == QuestionType.RECOGNITION || type == QuestionType.CORRECTNESS;
    }

    private boolean isImplementationQuestion(QuestionType type) {
        return type == QuestionType.IMPLEMENTATION || type == QuestionType.DERIVATION ||
               type == QuestionType.COMPLEXITY || type == QuestionType.COUNTEREXAMPLE || 
               type == QuestionType.OPTIMIZATION;
    }
}
