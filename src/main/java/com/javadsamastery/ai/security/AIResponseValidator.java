package com.javadsamastery.ai.security;

import com.javadsamastery.domain.evidence.EvaluationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

public class AIResponseValidator {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static EvaluationResult validateAndExtractEvaluation(String rawAiResponse, java.util.List<String> allowedMisconceptions) {
        if (rawAiResponse == null || rawAiResponse.isBlank()) {
            return EvaluationResult.INVALID_RESPONSE;
        }

        String cleaned = cleanJson(rawAiResponse);

        try {
            JsonNode root = mapper.readTree(cleaned);
            
            // Explicitly deny authorization/mutation keywords in the root structure
            if (root.has("action") || root.has("set_mastery") || root.has("mutate_state")) {
                return EvaluationResult.INVALID_RESPONSE;
            }


            if (root.has("misconceptionCategory")) {
                String cat = root.get("misconceptionCategory").asText();
                if (cat != null && !cat.isBlank() && !cat.equalsIgnoreCase("null") && !cat.equalsIgnoreCase("none")) {
                    if (allowedMisconceptions == null || !allowedMisconceptions.contains(cat)) {
                        return EvaluationResult.INVALID_RESPONSE;
                    }
                }
            }

            if (!root.has("evaluation")) {

                return EvaluationResult.INVALID_RESPONSE;
            }

            String evalString = root.get("evaluation").asText().toUpperCase();
            return switch (evalString) {
                case "PASS" -> EvaluationResult.PASS;
                case "FAIL" -> EvaluationResult.FAIL;
                case "PARTIAL" -> EvaluationResult.PARTIAL;
                case "HINT" -> EvaluationResult.HINT;
                default -> EvaluationResult.INVALID_RESPONSE;
            };

        } catch (Exception e) {
            // If the AI didn't return valid JSON, it's invalid
            return EvaluationResult.INVALID_RESPONSE;
        }
    }
    
    public static String extractMisconception(String rawAiResponse, java.util.List<String> allowedMisconceptions) {
        if (rawAiResponse == null || rawAiResponse.isBlank()) {
            return null;
        }
        try {
            JsonNode root = mapper.readTree(cleanJson(rawAiResponse));
            if (root.has("misconceptionCategory")) {
                String cat = root.get("misconceptionCategory").asText();
                if (cat == null || cat.isBlank() || cat.equalsIgnoreCase("null") || cat.equalsIgnoreCase("none")) {
                    return null;
                }
                return (allowedMisconceptions != null && allowedMisconceptions.contains(cat)) ? cat : null;
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private static String cleanJson(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.startsWith("```json")) {
            s = s.substring(7);
        } else if (s.startsWith("```")) {
            s = s.substring(3);
        }
        if (s.endsWith("```")) {
            s = s.substring(0, s.length() - 3);
        }
        return s.trim();
    }
}
