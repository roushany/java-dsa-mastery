package com.javadsamastery.domain.curriculum;

import com.javadsamastery.domain.lesson.LearningState;
import java.util.List;
import java.util.Map;

public record TopicDefinition(
    String topicId,
    String title,
    List<String> prerequisites,
    String difficulty,
    Map<LearningState, StageRequirement> stageRequirements
) {
    public boolean hasStage(LearningState state) {
        if (stageRequirements == null) return true;
        return stageRequirements.getOrDefault(state, StageRequirement.REQUIRED) != StageRequirement.NOT_APPLICABLE;
    }
}
