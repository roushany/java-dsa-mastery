package com.javadsamastery.dto;

import java.util.List;

public record TopicSummaryDto(
    String topicId,
    String title,
    String difficulty,
    List<String> prerequisites,
    boolean hasContent,
    int totalStages
) {}
