package com.javadsamastery.ai;

import com.javadsamastery.ai.dto.*;
import com.javadsamastery.ai.mock.MockLLMProvider;
import com.javadsamastery.ai.service.impl.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AIProviderIntegrationTest {

    private MockLLMProvider mockProvider;

    @BeforeEach
    void setUp() {
        mockProvider = new MockLLMProvider();
    }

    @Test
    void testAnswerEvaluator_Success() {
        mockProvider.setCannedResponse(String.class, "{\"evaluation\": \"PASS\", \"feedback\": \"Perfect\"}");
        LLMAnswerEvaluator evaluator = new LLMAnswerEvaluator(mockProvider);
        
        EvaluationResultDto result = evaluator.evaluate("Q?", "A", "C", null);
        assertEquals("PASS", result.evaluation());
    }

    @Test
    void testAnswerEvaluator_SafeFallbackOnException() {
        mockProvider.setSimulateFailure(true);
        LLMAnswerEvaluator evaluator = new LLMAnswerEvaluator(mockProvider);
        
        assertThrows(com.javadsamastery.ai.provider.AIProviderException.class, () -> evaluator.evaluate("Q?", "A", "C", null));
    }

    @Test
    void testHintGenerator_Success() {
        mockProvider.setCannedResponse(HintDto.class, new HintDto("Try this", false));
        LLMHintGenerator generator = new LLMHintGenerator(mockProvider);
        
        HintDto result = generator.generateHint("p1", "code", 1);
        assertEquals("Try this", result.hintText());
        assertFalse(result.isFinalHint());
    }

    @Test
    void testMisconceptionDetector_SafeFallback() {
        mockProvider.setSimulateFailure(true);
        LLMMisconceptionDetector detector = new LLMMisconceptionDetector(mockProvider);
        
        MisconceptionDto result = detector.detect("Ans", "Ctx");
        assertFalse(result.hasMisconception()); // Defaults safely
    }

    @Test
    void testQuestionGenerator_Success() {
        mockProvider.setCannedResponse(GeneratedQuestion.class, new GeneratedQuestion("What is it?", "Expected", "HARD"));
        LLMQuestionGenerator generator = new LLMQuestionGenerator(mockProvider);
        
        GeneratedQuestion q = generator.generateQuestion("topic", "HARD");
        assertEquals("What is it?", q.questionText());
        assertEquals("Expected", q.expectedConcept());
    }

    @Test
    void testTutor_Success() {
        mockProvider.setCannedResponse(String.class, "Explained clearly.");
        LLMTutor tutor = new LLMTutor(mockProvider);
        
        String result = tutor.explainConcept("Arrays", "confused");
        assertEquals("Explained clearly.", result);
    }
}
