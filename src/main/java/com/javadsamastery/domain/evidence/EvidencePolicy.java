package com.javadsamastery.domain.evidence;

import com.javadsamastery.ai.dto.AIAssessment;
import com.javadsamastery.domain.lesson.LearningState;
import java.time.Instant;

public class EvidencePolicy {

    /**
     * Authorizes an AI assessment, converting it into authoritative evidence only if it matches 
     * the requested context and adheres to deterministic policy.
     */
    public static EvidenceRecord authorize(
            AIAssessment assessment, 
            AttemptContext context, 
            int hintCount,
            boolean deterministicImplementationPassed,
            java.util.Set<String> validMisconceptions) {
        
        // 1. Attempt Binding (Stale/Replay Protection)
        if (!context.attemptId().equals(assessment.attemptId())) {
            return fallbackEvidence(context, hintCount, "Stale or replayed attempt ID");
        }
        
        // 2. Context Binding (Topic/Question Swap Protection)
        if (!context.topicId().equals(assessment.topicId())) {
            return fallbackEvidence(context, hintCount, "Topic mismatch injection");
        }
        if (!context.questionId().equals(assessment.questionId())) {
            return fallbackEvidence(context, hintCount, "Question ID mismatch injection");
        }
        
        EvaluationResult finalResult = assessment.evaluation();
        
        // 3. Implementation Evidence (Deterministic execution is authoritative)
        if (context.expectedState() == LearningState.IMPLEMENTATION || context.expectedState() == LearningState.DEBUGGING) {
            // Even if AI hallucinates PASS for a bad implementation, the tests must pass
            if (!deterministicImplementationPassed) {
                finalResult = EvaluationResult.FAIL;
            }
        }
        
        // 4. Transfer Evidence
        if (context.expectedState() == LearningState.TRANSFER) {
            // If transfer answer is identical to original answer, it fails.
            // Simplified: we rely on context validation for now. We can explicitly reject PASS if needed.
        }

        // 5. Misconception Validation
        String proposedMisconception = assessment.misconceptionCategory();
        if (proposedMisconception != null && validMisconceptions != null && !validMisconceptions.contains(proposedMisconception)) {
            proposedMisconception = null; // Silently reject unsupported misconception
        }

        return new EvidenceRecord(
            context.learnerId(),
            context.topicId(),
            context.questionId(),
            context.difficulty(),
            context.questionType(),
            hintCount,
            context.expectedState() == LearningState.RETRIEVAL,

            finalResult,
            proposedMisconception,
            Instant.now()
        );
    }
    
    private static EvidenceRecord fallbackEvidence(AttemptContext context, int hintCount, String reason) {
        return new EvidenceRecord(
            context.learnerId(),
            context.topicId(),
            context.questionId(),
            context.difficulty(),
            context.questionType(),
            hintCount,
            context.expectedState() == LearningState.RETRIEVAL,

            EvaluationResult.INVALID_RESPONSE, // safely reject without pedagogical penalty
            null, // No valid misconception for invalid response
            Instant.now()
        );
    }
}
