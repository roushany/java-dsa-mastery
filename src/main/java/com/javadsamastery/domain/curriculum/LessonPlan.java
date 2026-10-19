package com.javadsamastery.domain.curriculum;

import com.javadsamastery.domain.lesson.LearningState;
import java.util.ArrayList;
import java.util.List;

/**
 * The deterministic plan of nominal stages for a specific topic, derived from Curriculum config.
 */
public class LessonPlan {
    private final List<LearningState> nominalPath;

    // The full Golden Measure sequence.
    private static final List<LearningState> GOLDEN_MEASURE = List.of(
        LearningState.CONTENT_INTRO,
        LearningState.CONCEPT_ASSESSMENT,
        LearningState.DERIVE,
        LearningState.IMPLEMENTATION,
        LearningState.EXPLANATION,
        LearningState.COUNTEREXAMPLE,
        LearningState.OPTIMIZE,
        LearningState.TRANSFER,
        LearningState.RETRIEVAL
    );

    public LessonPlan(TopicDefinition definition) {
        this.nominalPath = new ArrayList<>();
        for (LearningState state : GOLDEN_MEASURE) {
            if (definition.hasStage(state)) {
                nominalPath.add(state);
            }
        }
        nominalPath.add(LearningState.COMPLETED); // Always end in COMPLETED
    }

    public List<LearningState> getNominalPath() {
        return nominalPath;
    }
}
