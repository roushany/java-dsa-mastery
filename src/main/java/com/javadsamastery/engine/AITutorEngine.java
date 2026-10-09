package com.javadsamastery.engine;

import com.javadsamastery.ai.service.Tutor;
import com.javadsamastery.ai.service.QuestionGenerator;
import com.javadsamastery.ai.service.AnswerEvaluator;
import com.javadsamastery.ai.dto.GeneratedQuestion;
import com.javadsamastery.ai.dto.EvaluationResultDto;
import com.javadsamastery.domain.learner.LearnerProfile;
import com.javadsamastery.domain.lesson.QuestionType;
import com.javadsamastery.domain.evidence.EvidenceRecord;
import com.javadsamastery.domain.evidence.EvaluationResult;
import com.javadsamastery.domain.problem.ProblemDifficulty;
import com.javadsamastery.ai.dto.AIAssessment;
import java.time.Instant;
import java.util.UUID;

/**
 * The AI Tutor Orchestrator.
 * Acts as the teacher, explaining concepts and evaluating answers.
 * It is completely decoupled from state mutation; it purely translates context into Observable Evidence.
 */
public class AITutorEngine {
    private final Tutor explainEngine;
    private final QuestionGenerator questionEngine;
    private final AnswerEvaluator evaluateEngine;

    public AITutorEngine(Tutor explainEngine, QuestionGenerator questionEngine, AnswerEvaluator evaluateEngine) {
        this.explainEngine = explainEngine;
        this.questionEngine = questionEngine;
        this.evaluateEngine = evaluateEngine;
    }

    public String explain(String topicId, LearnerProfile profile) {
        String context = "Current Mastery: " + profile.getMastery(topicId).name();
        return explainEngine.explainConcept(topicId, context);
    }

    public GeneratedQuestion generateQuestion(String topicId, QuestionType requestedType, LearnerProfile profile) {
        // The AdaptiveQuestionEngine decides the TYPE. The AI Tutor generates the content to fit that type.
        String context = profile.getMastery(topicId).name() + " - Pedagogy Mode: " + requestedType.name();
        return questionEngine.generateQuestion(topicId, context);
    }

    public AIAssessment assessAnswer(
            String attemptId,
            String topicId, 
            String questionId,
            String questionText, 
            String expectedConcept, 
            String learnerAnswer,
            java.util.List<String> allowedMisconceptions) {
            
        try {
            EvaluationResultDto dto = evaluateEngine.evaluate(questionText, learnerAnswer, expectedConcept, allowedMisconceptions);
            EvaluationResult res = EvaluationResult.valueOf(dto.evaluation());
            
            return new AIAssessment(
                attemptId,
                topicId,
                questionId,
                res,
                dto.feedback(),
                dto.misconceptionCategory()
            );
        } catch (com.javadsamastery.ai.provider.AIProviderException e) {
            EvaluationResult failRes = EvaluationResult.PROVIDER_FAILURE;
            if (e.getMessage() != null && e.getMessage().contains("TIMEOUT")) {
                failRes = EvaluationResult.PROVIDER_TIMEOUT;
            }
            return new AIAssessment(
                attemptId,
                topicId,
                questionId,
                failRes,
                "Infrastructure failure: " + e.getMessage(),
                null
            );
        }
    }
}
