package com.javadsamastery.ai.security;

import com.javadsamastery.domain.evidence.EvaluationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AIResponseValidatorMarkdownTest {

    @Test
    void testMarkdownWrappedJsonParsesCorrectly() {
        String wrapped = "```json\n" +
                         "{\n" +
                         "  \"evaluation\": \"PASS\",\n" +
                         "  \"misconceptionCategory\": null,\n" +
                         "  \"feedback\": \"Great job.\"\n" +
                         "}\n" +
                         "```";
                         
        EvaluationResult result = AIResponseValidator.validateAndExtractEvaluation(wrapped, null);
        assertEquals(EvaluationResult.PASS, result, "Validator should successfully parse JSON wrapped in markdown ticks.");
        
        String misc = AIResponseValidator.extractMisconception(wrapped, null);
        assertEquals(null, misc);
    }
}
