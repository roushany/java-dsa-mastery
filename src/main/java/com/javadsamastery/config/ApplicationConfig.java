package com.javadsamastery.config;

import com.javadsamastery.ai.mock.MockLLMProvider;
import com.javadsamastery.ai.provider.GeminiLLMProvider;
import com.javadsamastery.ai.provider.LLMProvider;
import com.javadsamastery.ai.service.impl.LLMAnswerEvaluator;
import com.javadsamastery.ai.service.impl.LLMQuestionGenerator;
import com.javadsamastery.ai.service.impl.LLMTutor;
import com.javadsamastery.domain.content.QuestionBank;
import com.javadsamastery.domain.curriculum.CurriculumLoader;
import com.javadsamastery.domain.topic.CurriculumGraph;
import com.javadsamastery.engine.AITutorEngine;
import com.javadsamastery.engine.MasteryEngine;
import com.javadsamastery.engine.QuestionSelector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ApplicationConfig {

    private static final List<String> KNOWN_TOPICS = List.of(
            "linear-search",
            "binary-search",
            "two-pointers",
            "sliding-window",
            "prefix-sum",
            "hashing",
            "binary-search-on-answer"
    );

    @Bean
    public CurriculumGraph curriculumGraph() {
        return CurriculumLoader.loadCurriculum("/curriculum/curriculum.json");
    }

    @Bean
    public QuestionBank questionBank() {
        QuestionBank bank = new QuestionBank();
        for (String topic : KNOWN_TOPICS) {
            bank.loadFromJsonResource("/questions/" + topic + ".json");
        }
        return bank;
    }

    @Bean
    public QuestionSelector questionSelector(QuestionBank questionBank) {
        return new QuestionSelector(questionBank);
    }

    @Bean
    public MasteryEngine masteryEngine() {
        return new MasteryEngine();
    }

    @Bean
    public LLMProvider llmProvider(
            @Value("${gemini.api.key:#{null}}") String configuredKey,
            @Value("${spring.profiles.active:default}") String activeProfiles) {
        String key = (configuredKey != null && !configuredKey.isBlank())
                ? configuredKey
                : System.getenv("GEMINI_API_KEY");

        if (key != null && !key.isBlank()) {
            return new GeminiLLMProvider();
        }

        if (activeProfiles != null && (activeProfiles.contains("prod") || activeProfiles.contains("production"))) {
            throw new IllegalStateException("FATAL CONFIGURATION ERROR: GEMINI_API_KEY must be configured in production profile. Silent fallback to MockLLMProvider is strictly forbidden in production.");
        }

        // Safe deterministic mock provider for CI, testing, and local development without external API keys
        MockLLMProvider fallbackMock = new MockLLMProvider();
        fallbackMock.setCannedResponse(
                String.class,
                "{\"evaluation\": \"PASS\", \"confidenceScore\": 0, \"feedback\": \"Authoritative fallback evaluation.\"}"
        );
        return fallbackMock;
    }

    @Bean
    public AITutorEngine aiTutorEngine(LLMProvider provider) {
        return new AITutorEngine(
                new LLMTutor(provider),
                new LLMQuestionGenerator(provider),
                new LLMAnswerEvaluator(provider)
        );
    }
}
