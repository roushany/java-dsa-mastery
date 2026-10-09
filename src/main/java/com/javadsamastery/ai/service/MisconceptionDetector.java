package com.javadsamastery.ai.service;

import com.javadsamastery.ai.dto.MisconceptionDto;

public interface MisconceptionDetector {
    MisconceptionDto detect(String learnerAnswer, String topicContext);
}
