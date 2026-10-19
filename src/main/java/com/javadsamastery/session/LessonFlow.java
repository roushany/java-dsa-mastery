package com.javadsamastery.session;

import com.javadsamastery.domain.lesson.LearningState;
import com.javadsamastery.domain.evidence.EvaluationResult;
import com.javadsamastery.domain.evidence.EvidenceRecord;
import com.javadsamastery.domain.learner.LearnerProfile;
import com.javadsamastery.domain.curriculum.TopicDefinition;
import com.javadsamastery.domain.curriculum.LessonPlan;
import com.javadsamastery.domain.content.TopicTemplate;
import com.javadsamastery.engine.MasteryEngine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LessonFlow {
    private LearningState activeState;
    private final TopicDefinition topicDef;
    private final TopicTemplate content;
    private final LessonPlan lessonPlan;
    private final LearnerProfile profile;
    private final MasteryEngine masteryEngine;
    private final List<EvidenceRecord> history = new ArrayList<>();
    
    private int pedagogicalFailures = 0;
    private int totalAttemptsInState = 0;
    private int nominalStageIndex = 0;
    
    public LessonFlow(TopicDefinition topicDef, TopicTemplate content, LearnerProfile profile, MasteryEngine masteryEngine) {
        this.topicDef = topicDef;
        this.content = content;
        this.profile = profile;
        this.masteryEngine = masteryEngine;
        this.lessonPlan = new LessonPlan(topicDef);
        this.nominalStageIndex = 0;
        this.activeState = getNominalState();
    }

    public LearningState getCurrentState() {
        return activeState;
    }

    private LearningState getNominalState() {
        if (nominalStageIndex >= lessonPlan.getNominalPath().size()) {
            return LearningState.COMPLETED;
        }
        return lessonPlan.getNominalPath().get(nominalStageIndex);
    }

    public void advanceIntro() {
        if (activeState == LearningState.CONTENT_INTRO) {
            nominalStageIndex++;
            transitionTo(getNominalState());
        } else {
            throw new IllegalStateException("Invalid transition from " + activeState);
        }
    }

    public void submitEvidence(EvidenceRecord record) {
        if (activeState == LearningState.COMPLETED || activeState == LearningState.FAILED_TERMINATED) {
            throw new IllegalStateException("Session already terminated.");
        }

        history.add(record);
        masteryEngine.evaluateMastery(profile, topicDef.topicId(), history);
        
        EvaluationResult result = record.result();
        totalAttemptsInState++;

        // 1. Anti-Loop Protection (Abuse / Bot prevention)
        if (totalAttemptsInState >= 10) {
            transitionTo(LearningState.FAILED_TERMINATED);
            return;
        }

        // 2. Safely ignore AI hallucinations and provider errors from advancing/failing the learner pedagogically
        if (result == EvaluationResult.INVALID_RESPONSE || result == EvaluationResult.HINT || result == EvaluationResult.PARTIAL || result == EvaluationResult.PROVIDER_FAILURE || result == EvaluationResult.PROVIDER_TIMEOUT) {
            return; // Stay in current state
        }

        // 3. Track pedagogical failure
        if (result == EvaluationResult.FAIL) {
            pedagogicalFailures++;
            if (pedagogicalFailures >= 3 && activeState != LearningState.SOLUTION_WALKTHROUGH) {
                transitionTo(LearningState.SOLUTION_WALKTHROUGH);
                return;
            }
        } else if (result == EvaluationResult.PASS) {
            pedagogicalFailures = 0;
        }

        // 4. Deterministic Graph Transitions
        if (result == EvaluationResult.FAIL) {
            if (activeState == LearningState.IMPLEMENTATION) {
                transitionTo(LearningState.DEBUGGING);
            } else if (activeState != LearningState.SOLUTION_WALKTHROUGH && activeState != LearningState.DEBUGGING && activeState != LearningState.REMEDIATION) {
                transitionTo(LearningState.REMEDIATION);
            }
        } else if (result == EvaluationResult.PASS) {
            // If they were in a cycle state and pass, they rejoin the nominal path
            if (activeState == LearningState.REMEDIATION) {
                // When you pass remediation, you return to the state you were stuck on
                transitionTo(getNominalState());
            } else if (activeState == LearningState.DEBUGGING) {
                // Debugging passes act like an Implementation pass; advance to next
                nominalStageIndex++;
                transitionTo(getNominalState());
            } else if (activeState == LearningState.SOLUTION_WALKTHROUGH) {
                transitionTo(LearningState.COMPLETED);
            } else {
                // Normal advancement
                nominalStageIndex++;
                transitionTo(getNominalState());
            }
        }
    }
    
    private void transitionTo(LearningState nextState) {
        this.activeState = nextState;
        this.totalAttemptsInState = 0;
    }
    
    public TopicDefinition getTopicDef() {
        return topicDef;
    }
    
    public TopicTemplate getContent() {
        return content;
    }
    
    public List<EvidenceRecord> getHistory() {
        return Collections.unmodifiableList(history);
    }

    public int getNominalStageIndex() {
        return nominalStageIndex;
    }

    public int getPedagogicalFailures() {
        return pedagogicalFailures;
    }

    public int getTotalAttemptsInState() {
        return totalAttemptsInState;
    }

    public static LessonFlow rehydrate(
            TopicDefinition topicDef,
            TopicTemplate content,
            LearnerProfile profile,
            MasteryEngine masteryEngine,
            LearningState activeState,
            int nominalStageIndex,
            int pedagogicalFailures,
            int totalAttemptsInState,
            List<EvidenceRecord> history
    ) {
        LessonFlow flow = new LessonFlow(topicDef, content, profile, masteryEngine);
        flow.activeState = activeState;
        flow.nominalStageIndex = nominalStageIndex;
        flow.pedagogicalFailures = pedagogicalFailures;
        flow.totalAttemptsInState = totalAttemptsInState;
        flow.history.clear();
        if (history != null) {
            flow.history.addAll(history);
        }
        return flow;
    }
}
