package com.javadsamastery.ai.service;

import com.javadsamastery.ai.dto.HintDto;

public interface HintGenerator {
    HintDto generateHint(String problemId, String learnerContext, int currentHintLevel);
}
