package com.javadsamastery.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class GeminiLLMProvider implements LLMProvider {
    
    protected String apiKey;
    private final String model;
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final String baseUrl;
    
    public GeminiLLMProvider() {
        this.apiKey = System.getenv("GEMINI_API_KEY");
        this.baseUrl = System.getProperty("gemini.api.baseurl", "https://generativelanguage.googleapis.com/v1beta/models/");
        String envModel = System.getenv("GEMINI_MODEL");
        this.model = (envModel != null && !envModel.isBlank()) ? envModel : "gemini-3.8-flash";
        
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper = new ObjectMapper();
        this.mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public String getModelName() {
        return this.model;
    }

    @Override
    public <T> T generateStructured(String systemPrompt, String userPrompt, Class<T> responseType) throws AIProviderException {
        // We only support EvaluationResultDto schema enforcing at the provider level for now.
        // Other types will just use application/json.
        boolean useEvalSchema = responseType.getSimpleName().equals("EvaluationResultDto");
        String rawJson = generateRawWithSchema(systemPrompt, userPrompt, useEvalSchema);
        try {
            return mapper.readValue(rawJson, responseType);
        } catch (Exception e) {
            throw new AIProviderException("Failed to parse structured JSON response from Gemini", e);
        }
    }

    @Override
    public String generateRaw(String systemPrompt, String userPrompt) throws AIProviderException {
        return generateRawWithSchema(systemPrompt, userPrompt, false);
    }

    private String generateRawWithSchema(String systemPrompt, String userPrompt, boolean enforceEvalSchema) throws AIProviderException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AIProviderException("PROVIDER_FAILURE: GEMINI_API_KEY is missing");
        }

        try {
            var sysPart = mapper.createObjectNode().put("text", systemPrompt);
            var sysParts = mapper.createArrayNode().add(sysPart);
            var systemInstruction = mapper.createObjectNode();
            systemInstruction.put("role", "system");
            systemInstruction.set("parts", sysParts);

            var userPart = mapper.createObjectNode().put("text", userPrompt);
            var userParts = mapper.createArrayNode().add(userPart);
            var content = mapper.createObjectNode();
            content.put("role", "user");
            content.set("parts", userParts);
            var contents = mapper.createArrayNode().add(content);

            var genConfig = mapper.createObjectNode()
                .put("responseMimeType", "application/json");

            if (enforceEvalSchema) {
                genConfig.set("responseSchema", buildEvaluationSchema());
            }

            var requestBody = mapper.createObjectNode();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                requestBody.set("systemInstruction", systemInstruction);
            }
            requestBody.set("contents", contents);
            requestBody.set("generationConfig", genConfig);

            String url = baseUrl + model + ":generateContent";

            System.out.println("[AI DEBUG] provider class=" + this.getClass().getName());
            System.out.println("[AI DEBUG] method invoked=generateRawWithSchema");
            System.out.println("[AI DEBUG] model=" + model);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            System.out.println("[AI DEBUG] HTTP status=" + status);
            
            if (status != 200) {
                System.out.println("[AI DEBUG] Gemini API returned HTTP " + status);
                System.out.println("[AI DEBUG] Endpoint: " + url);
                System.out.println("[AI DEBUG] Model: " + model);
                try {
                    JsonNode errRoot = mapper.readTree(response.body());
                    if (errRoot.has("error")) {
                        JsonNode err = errRoot.get("error");
                        if (err.has("status")) System.out.println("[AI DEBUG] Error Status: " + err.get("status").asText());
                        if (err.has("message")) System.out.println("[AI DEBUG] Error Message: " + err.get("message").asText());
                    } else {
                        System.out.println("[AI DEBUG] Error Body: " + response.body());
                    }
                } catch (Exception parseEx) {
                    System.out.println("[AI DEBUG] Non-JSON Error Body: " + response.body());
                }
            }

            if (status == 401 || status == 403) {
                throw new AIProviderException("PROVIDER_FAILURE: Gemini API authentication failed (HTTP " + status + ").");
            } else if (status == 429) {
                throw new AIProviderException("PROVIDER_FAILURE: Gemini API rate limit exceeded (HTTP 429).");
            } else if (status >= 500) {
                throw new AIProviderException("PROVIDER_FAILURE: Gemini API server error (HTTP " + status + ").");
            } else if (status >= 400) {
                throw new AIProviderException("PROVIDER_FAILURE: Gemini API client error (HTTP " + status + ").");
            }

            JsonNode root = mapper.readTree(response.body());
            
            int candidateCount = 0;
            if (root.has("candidates") && root.get("candidates").isArray()) {
                candidateCount = root.get("candidates").size();
            }
            System.out.println("[AI DEBUG] candidate count=" + candidateCount);

            if (!root.has("candidates") || !root.get("candidates").isArray() || root.get("candidates").size() == 0) {
                if (root.has("promptFeedback") && root.get("promptFeedback").has("blockReason")) {
                    throw new AIProviderException("PROVIDER_FAILURE: Gemini API blocked the prompt. Reason: " + root.get("promptFeedback").get("blockReason").asText());
                }
                throw new AIProviderException("PROVIDER_FAILURE: Gemini API returned no candidates and no promptFeedback.");
            }

            JsonNode firstCandidate = root.get("candidates").get(0);
            
            if (firstCandidate.has("finishReason")) {
                String finishReason = firstCandidate.get("finishReason").asText();
                System.out.println("[AI DEBUG] finishReason=" + finishReason);
                if ("SAFETY".equalsIgnoreCase(finishReason)) {
                    throw new AIProviderException("PROVIDER_FAILURE: Gemini API response blocked due to SAFETY finishReason.");
                } else if (!"STOP".equalsIgnoreCase(finishReason)) {
                    throw new AIProviderException("PROVIDER_FAILURE: Gemini API response stopped unexpectedly. finishReason: " + finishReason);
                }
            }

            System.out.println("[AI DEBUG] response content present=" + (firstCandidate.has("content") && firstCandidate.get("content").has("parts")));

            if (firstCandidate.has("content") && firstCandidate.get("content").has("parts")) {
                JsonNode parts = firstCandidate.get("content").get("parts");
                if (parts.isArray() && parts.size() > 0) {
                     String text = parts.get(0).get("text").asText();
                     if (text == null || text.isBlank()) {
                         throw new AIProviderException("PROVIDER_FAILURE: Gemini API returned empty text part.");
                     }
                     System.out.println("[AI DEBUG] response text length=" + text.length());
                     return text.trim();
                }
            }
            throw new AIProviderException("PROVIDER_FAILURE: Unexpected response structure from Gemini: missing content parts.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AIProviderException("PROVIDER_FAILURE: Gemini API request interrupted.", e);
        } catch (java.net.http.HttpTimeoutException e) {
            throw new AIProviderException("PROVIDER_TIMEOUT: Gemini API request timed out after 30 seconds.", e);
        } catch (AIProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new AIProviderException("PROVIDER_FAILURE: Error communicating with Gemini API: " + e.getMessage(), e);
        }
    }

    private JsonNode buildEvaluationSchema() {
        var schema = mapper.createObjectNode();
        schema.put("type", "OBJECT");
        
        var properties = mapper.createObjectNode();
        
        var evalProp = mapper.createObjectNode();
        evalProp.put("type", "STRING");
        var enumArray = mapper.createArrayNode();
        enumArray.add("PASS").add("FAIL").add("PARTIAL").add("INVALID_RESPONSE");
        evalProp.set("enum", enumArray);
        properties.set("evaluation", evalProp);

        var confProp = mapper.createObjectNode();
        confProp.put("type", "INTEGER");
        properties.set("confidenceScore", confProp);

        var miscProp = mapper.createObjectNode();
        miscProp.put("type", "STRING");
        properties.set("misconceptionCategory", miscProp);

        var fbProp = mapper.createObjectNode();
        fbProp.put("type", "STRING");
        properties.set("feedback", fbProp);

        var remProp = mapper.createObjectNode();
        remProp.put("type", "STRING");
        properties.set("recommendedRemediation", remProp);

        schema.set("properties", properties);

        var required = mapper.createArrayNode();
        required.add("evaluation").add("confidenceScore").add("feedback");
        schema.set("required", required);

        return schema;
    }
}
