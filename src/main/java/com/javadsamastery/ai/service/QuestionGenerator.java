package com.javadsamastery.ai.service;

import com.javadsamastery.ai.dto.GeneratedQuestion;

public interface QuestionGenerator {
    GeneratedQuestion generateQuestion(String topicId, String difficulty);
}
