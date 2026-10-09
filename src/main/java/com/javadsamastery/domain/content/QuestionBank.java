package com.javadsamastery.domain.content;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javadsamastery.domain.lesson.LearningState;
import com.javadsamastery.domain.problem.ProblemDifficulty;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuestionBank {
    private static final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final List<QuestionDefinition> questions = new ArrayList<>();

    public void loadFromJsonStream(InputStream is) {
        try {
            List<QuestionDefinition> loaded = mapper.readValue(is, new TypeReference<List<QuestionDefinition>>() {});
            questions.addAll(loaded);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to load question bank JSON", e);
        }
    }
    
    public void loadFromJsonResource(String resourcePath) {
        try (InputStream is = QuestionBank.class.getResourceAsStream(resourcePath)) {
            if (is == null) throw new IllegalArgumentException("Question resource not found: " + resourcePath);
            loadFromJsonStream(is);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to load resource " + resourcePath, e);
        }
    }

    public List<QuestionDefinition> getQuestions() {
        return Collections.unmodifiableList(questions);
    }
    
    public List<QuestionDefinition> findQuestions(String topicId, LearningState stage, ProblemDifficulty difficulty) {
        return questions.stream()
            .filter(q -> q.topicId().equals(topicId))
            .filter(q -> q.targetStage() == stage)
            .filter(q -> q.difficulty() == difficulty)
            .toList();
    }
}
