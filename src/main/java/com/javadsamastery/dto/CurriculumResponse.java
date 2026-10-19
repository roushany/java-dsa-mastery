package com.javadsamastery.dto;

import java.util.List;

public record CurriculumResponse(
    List<TopicSummaryDto> topics
) {}
