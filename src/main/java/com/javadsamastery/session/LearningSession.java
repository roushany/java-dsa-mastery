package com.javadsamastery.session;

import com.javadsamastery.domain.learner.LearnerProfile;
import com.javadsamastery.engine.MasteryEngine;
import com.javadsamastery.engine.AITutorEngine;
import com.javadsamastery.domain.lesson.QuestionType;
import com.javadsamastery.ai.dto.GeneratedQuestion;
import com.javadsamastery.domain.evidence.EvidenceRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Orchestrates a learning session for a specific learner profile.
 * Coordinates between the deterministic AdaptiveQuestionEngine/MasteryEngine
 * and the probabilistic AITutorEngine.
 */
public class LearningSession {
    private final LearnerProfile learnerProfile;
    private final MasteryEngine masteryEngine;
    private final com.javadsamastery.engine.QuestionSelector questionSelector;
    private final AITutorEngine tutor;
    
    private final List<EvidenceRecord> sessionHistory = new ArrayList<>();
    private boolean active;

    public LearningSession(
            LearnerProfile learnerProfile, 
            MasteryEngine masteryEngine, 
            com.javadsamastery.engine.QuestionSelector questionSelector, 
            AITutorEngine tutor) {
        this.learnerProfile = learnerProfile;
        this.masteryEngine = masteryEngine;
        this.questionSelector = questionSelector;
        this.tutor = tutor;
        this.active = false;
    }

    // New state to track the active attempt context
    private com.javadsamastery.domain.evidence.AttemptContext activeContext;

    public void start() {
        this.active = true;
    }

    public void end() {
        this.active = false;
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Determines what specific question is needed, picking from the QuestionBank.
     */
    public GeneratedQuestion getNextQuestion(String topicId, com.javadsamastery.domain.lesson.LearningState currentObjective) {
        // Ensure state is updated before selection
        masteryEngine.evaluateMastery(learnerProfile, topicId, sessionHistory);
        com.javadsamastery.domain.learner.TopicState state = learnerProfile.getTopicState(topicId);
        
        com.javadsamastery.engine.QuestionSelectionResult selection = questionSelector.selectNextQuestion(state, currentObjective);
        com.javadsamastery.domain.content.QuestionDefinition def = selection.selectedQuestion();
        
        // Setup new context for the learner's attempt
        this.activeContext = new com.javadsamastery.domain.evidence.AttemptContext(
            java.util.UUID.randomUUID().toString(),
            topicId,
            def.questionId(),
            currentObjective, 
            mapToQuestionType(currentObjective), // Map LearningState to QuestionType
            def.difficulty(),
            learnerProfile.getLearnerId(),
            def.allowedMisconceptions()
        );
        
        // Pass the static definition down to the tutor to format
        // (In a full implementation, we wouldn't need a GeneratedQuestion from the LLM anymore, 
        // we'd just serve the QuestionDefinition directly. But to maintain the interface:)
        return new GeneratedQuestion(
            def.content(),
            def.expectedEvidence(),
            def.difficulty().name()
        );
    }
    
    private QuestionType mapToQuestionType(com.javadsamastery.domain.lesson.LearningState state) {
        return switch (state) {
            case CONTENT_INTRO -> QuestionType.INTUITION;
            case CONCEPT_ASSESSMENT -> QuestionType.CONCEPT;
            case DERIVE -> QuestionType.DERIVATION;
            case IMPLEMENTATION -> QuestionType.IMPLEMENTATION;
            case EXPLANATION -> QuestionType.CORRECTNESS;
            case COUNTEREXAMPLE -> QuestionType.COUNTEREXAMPLE;
            case OPTIMIZE -> QuestionType.OPTIMIZATION;
            case TRANSFER -> QuestionType.TRANSFER;
            case DEBUGGING -> QuestionType.DEBUGGING;
            case REMEDIATION -> QuestionType.DEBUGGING;
            default -> QuestionType.CONCEPT;
        };
    }

    /**
     * Updates expected state - allows tests to mock state transitions if LessonFlow isn't directly wired
     */
    public void setExpectedState(com.javadsamastery.domain.lesson.LearningState state) {
        if (activeContext != null) {
            activeContext = new com.javadsamastery.domain.evidence.AttemptContext(
                activeContext.attemptId(),
                activeContext.topicId(),
                activeContext.questionId(),
                state,
                activeContext.questionType(),
                activeContext.difficulty(),
                activeContext.learnerId(),
                activeContext.allowedMisconceptions()
            );
        }
    }

    /**
     * AI evaluates the answer to generate an Assessment.
     * The EvidencePolicy authorizes it to produce Authoritative Evidence.
     */
    public EvidenceRecord submitAnswer(
            String expectedConcept, 
            String answer, 
            int hintCount,
            boolean codeTestsPassed) {
            
        if (activeContext == null) throw new IllegalStateException("No active question context");
            
        // 1. AI evaluation yields raw untrusted Assessment
        com.javadsamastery.ai.dto.AIAssessment assessment = tutor.assessAnswer(
            activeContext.attemptId(),
            activeContext.topicId(),
            activeContext.questionId(),
            "dummy text", // simplified
            expectedConcept,
            answer,
            activeContext.allowedMisconceptions()
        );
        
        // 2. EvidencePolicy checks bounds and generates Authoritative Evidence
        java.util.Set<String> validMisc = questionSelector.getValidMisconceptions(activeContext.topicId());
        
        EvidenceRecord evidence = com.javadsamastery.domain.evidence.EvidencePolicy.authorize(
            assessment,
            activeContext,
            hintCount,
            codeTestsPassed,
            validMisc
        );
        
        // Prevent duplicate evidence submission for the same attempt
        String attemptTopicId = activeContext.topicId();
        this.activeContext = null;
        
        sessionHistory.add(evidence);
        
        // 3. Deterministic Mastery Engine updates profile state
        masteryEngine.evaluateMastery(learnerProfile, attemptTopicId, sessionHistory);
        
        return evidence;
    }
    
    // Test hook to inject an invalid attempt context to verify policy bounds
    public void overrideContext(com.javadsamastery.domain.evidence.AttemptContext badContext) {
        this.activeContext = badContext;
    }
    
    // For testing the feedback loop
    public com.javadsamastery.domain.evidence.AttemptContext getActiveContextForTest() {
        return activeContext;
    }
    
    public String requestExplanation(String topicId) {
        return tutor.explain(topicId, learnerProfile);
    }

    public List<EvidenceRecord> getSessionHistory() {
        return Collections.unmodifiableList(sessionHistory);
    }
    
    public LearnerProfile getLearnerProfile() {
        return learnerProfile;
    }

    public static LearningSession rehydrate(
            LearnerProfile learnerProfile,
            MasteryEngine masteryEngine,
            com.javadsamastery.engine.QuestionSelector questionSelector,
            AITutorEngine tutor,
            List<EvidenceRecord> history,
            com.javadsamastery.domain.evidence.AttemptContext activeContext,
            boolean active
    ) {
        LearningSession session = new LearningSession(learnerProfile, masteryEngine, questionSelector, tutor);
        if (history != null) {
            session.sessionHistory.addAll(history);
        }
        session.activeContext = activeContext;
        session.active = active;
        return session;
    }
}
