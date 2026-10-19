package com.javadsamastery.domain.learner;

import com.javadsamastery.domain.evidence.EvaluationResult;
import com.javadsamastery.domain.evidence.EvidenceRecord;
import com.javadsamastery.domain.topic.TopicMastery;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic learner model for a specific topic, derived from authoritative EvidenceRecords.
 */
public class TopicState {
    private final String topicId;
    
    // Core mastery
    private TopicMastery mastery = TopicMastery.NOVICE;
    
    // Recent performance
    private int consecutiveFailures = 0;
    private int hintCount = 0;
    
    // Strengths and weaknesses tracking
    private boolean derivationStrong = false;
    private boolean implementationStrong = false;
    private boolean transferStrong = false;
    
    // Misconceptions
    private final Set<String> activeMisconceptions = new HashSet<>();
    
    // Counters
    private int independentConceptPasses = 0;
    private int independentImplementationPasses = 0;
    private int transferPasses = 0;
    private int retrievalPasses = 0;
    
    public TopicState(String topicId) {
        this.topicId = topicId;
    }
    
    /**
     * Rebuilds the topic state from a chronologically sorted list of evidence records.
     */
    public void evaluateFromHistory(List<EvidenceRecord> topicHistory) {
        // Reset state
        mastery = TopicMastery.NOVICE;
        consecutiveFailures = 0;
        hintCount = 0;
        derivationStrong = false;
        implementationStrong = false;
        transferStrong = false;
        activeMisconceptions.clear();
        independentConceptPasses = 0;
        independentImplementationPasses = 0;
        transferPasses = 0;
        retrievalPasses = 0;
        
        for (EvidenceRecord record : topicHistory) {
            if (record.result() == EvaluationResult.INVALID_RESPONSE ||
                record.result() == EvaluationResult.PROVIDER_FAILURE ||
                record.result() == EvaluationResult.PROVIDER_TIMEOUT) {
                continue; // Do not penalize or reward
            }
            boolean isPass = record.result() == EvaluationResult.PASS;
            boolean isIndependent = record.hintCount() == 0;
            
            this.hintCount += record.hintCount();
            
            // Record Misconceptions
            if (record.misconceptionCategory() != null && !record.misconceptionCategory().isBlank()) {
                activeMisconceptions.add(record.misconceptionCategory());
            }
            
            if (!isPass) {
                consecutiveFailures++;
                if (record.questionType() == com.javadsamastery.domain.lesson.QuestionType.DERIVATION) {
                    derivationStrong = false;
                } else if (record.questionType() == com.javadsamastery.domain.lesson.QuestionType.IMPLEMENTATION) {
                    implementationStrong = false;
                } else if (record.questionType() == com.javadsamastery.domain.lesson.QuestionType.TRANSFER) {
                    transferStrong = false;
                }
                
                // Repeated misconception/failures reduce confidence
                if (consecutiveFailures >= 2) {
                    if (mastery == TopicMastery.MASTERED) {
                        mastery = TopicMastery.PROFICIENT;
                        transferPasses = 0;
                        retrievalPasses = 0;
                    } else if (mastery == TopicMastery.PROFICIENT) {
                        mastery = TopicMastery.FAMILIAR;
                        independentImplementationPasses = 0;
                        transferPasses = 0;
                    }
                }
                
                // Retrieval failure triggers remediation
                if (record.isRetrievalPractice()) {
                    mastery = TopicMastery.FAMILIAR;
                    independentImplementationPasses = 0;
                    retrievalPasses = 0;
                    transferPasses = 0;
                }
            } else {
                consecutiveFailures = 0;
                
                // Clear a misconception if we pass a task (simplified assumption: success implies overcoming recent hurdles)
                if (record.misconceptionCategory() != null && !record.misconceptionCategory().isBlank()) {
                      activeMisconceptions.remove(record.misconceptionCategory());
                  } else if (isIndependent) {
                      activeMisconceptions.clear(); // Independent success clears all
                  }
                
                // Only independent solutions increase mastery counts
                if (isIndependent) {
                    if (isConceptQuestion(record.questionType())) {
                        independentConceptPasses++;
                        derivationStrong = (record.questionType() == com.javadsamastery.domain.lesson.QuestionType.DERIVATION) || derivationStrong;
                    } else if (isImplementationQuestion(record.questionType())) {
                        independentImplementationPasses++;
                        implementationStrong = true;
                    } else if (record.questionType() == com.javadsamastery.domain.lesson.QuestionType.TRANSFER) {
                        transferPasses++;
                        transferStrong = true;
                    }
                    
                    if (record.isRetrievalPractice()) {
                        retrievalPasses++;
                    }
                }
            }

            // State Transitions
            if (mastery == TopicMastery.NOVICE && independentConceptPasses >= 2) {
                mastery = TopicMastery.FAMILIAR;
            }
            if (mastery == TopicMastery.FAMILIAR && independentImplementationPasses >= 2) {
                mastery = TopicMastery.PROFICIENT;
            }
            if (mastery == TopicMastery.PROFICIENT && transferPasses >= 1 && retrievalPasses >= 1) {
                mastery = TopicMastery.MASTERED;
            }
        }
    }
    
    private boolean isConceptQuestion(com.javadsamastery.domain.lesson.QuestionType type) {
        return type == com.javadsamastery.domain.lesson.QuestionType.CONCEPT || 
               type == com.javadsamastery.domain.lesson.QuestionType.INTUITION || 
               type == com.javadsamastery.domain.lesson.QuestionType.RECOGNITION || 
               type == com.javadsamastery.domain.lesson.QuestionType.CORRECTNESS ||
               type == com.javadsamastery.domain.lesson.QuestionType.DERIVATION;
    }

    private boolean isImplementationQuestion(com.javadsamastery.domain.lesson.QuestionType type) {
        return type == com.javadsamastery.domain.lesson.QuestionType.IMPLEMENTATION || 
               type == com.javadsamastery.domain.lesson.QuestionType.COMPLEXITY || 
               type == com.javadsamastery.domain.lesson.QuestionType.COUNTEREXAMPLE || 
               type == com.javadsamastery.domain.lesson.QuestionType.OPTIMIZATION;
    }

    // Getters
    public String getTopicId() { return topicId; }
    public TopicMastery getMastery() { return mastery; }
    public int getConsecutiveFailures() { return consecutiveFailures; }
    public Set<String> getActiveMisconceptions() { return Collections.unmodifiableSet(activeMisconceptions); }
    public boolean isDerivationStrong() { return derivationStrong; }
    public boolean isImplementationStrong() { return implementationStrong; }
    public boolean isTransferStrong() { return transferStrong; }
    public int getIndependentConceptPasses() { return independentConceptPasses; }
    public int getIndependentImplementationPasses() { return independentImplementationPasses; }
    public int getTransferPasses() { return transferPasses; }
    public int getRetrievalPasses() { return retrievalPasses; }
}
