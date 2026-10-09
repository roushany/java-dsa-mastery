package com.javadsamastery.domain.content;

public record TopicTemplate(
    String topicId,
    String title,
    String learningObjectives,
    String prerequisites,
    String intuition,
    String formalModel,
    String recognitionSignals,
    String derivation,
    String pseudocode,
    String javaImplementation,
    String complexity,
    String correctness,
    String edgeCases,
    String misconceptions,
    String counterexamples,
    String commonWrongApproaches,
    String comparisonWithAlternatives,
    String practiceProblems,
    String transferProblem,
    String retrievalPrompt
) {}
