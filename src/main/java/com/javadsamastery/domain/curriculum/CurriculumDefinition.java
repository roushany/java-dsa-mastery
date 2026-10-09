package com.javadsamastery.domain.curriculum;

import java.util.List;

public record CurriculumDefinition(
    List<TopicDefinition> topics
) {}
