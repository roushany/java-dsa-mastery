package com.javadsamastery.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javadsamastery.ai.mock.MockLLMProvider;
import com.javadsamastery.ai.provider.AIProviderException;
import com.javadsamastery.ai.provider.LLMProvider;
import com.javadsamastery.JavaDsaMasteryApplication;
import com.javadsamastery.security.LearnerTokenService;
import com.javadsamastery.security.SessionSecurityInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = JavaDsaMasteryApplication.class)
@AutoConfigureMockMvc
class LearningApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LLMProvider llmProvider;

    @Autowired
    private LearnerTokenService tokenService;

    @BeforeEach
    void resetMockProvider() {
        if (llmProvider instanceof MockLLMProvider mock) {
            mock.setCannedResponse(String.class, "{\"evaluation\": \"PASS\", \"confidenceScore\": 0, \"feedback\": \"Correct answer verified by deterministic mock.\"}");
        }
    }

    @Test
    void testHealthEndpoint_ReturnsUp() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.version").value("1.0.0"))
                .andExpect(jsonPath("$.activeTopics").value(7));
    }

    @Test
    void testCurriculumEndpoint_ReturnsTopics() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/curriculum"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topics").isArray())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(root.get("topics").size() >= 7, "Curriculum should expose all 7 implemented topics");
    }

    @Test
    void testStartSession_ValidTopic_ReturnsCreated() throws Exception {
        String payload = """
                {
                    "topicId": "binary-search",
                    "learnerId": "student-alice"
                }
                """;

        mockMvc.perform(post("/api/v1/sessions")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, tokenService.issueToken("student-alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.topicId").value("binary-search"))
                .andExpect(jsonPath("$.learnerId").value("student-alice"))
                .andExpect(jsonPath("$.currentState").value("CONTENT_INTRO"))
                .andExpect(jsonPath("$.mastery").value("NOVICE"));
    }

    @Test
    void testStartSession_InvalidTopic_Returns400() throws Exception {
        String payload = """
                {
                    "topicId": "non-existent-topic",
                    "learnerId": "student-bob"
                }
                """;

        mockMvc.perform(post("/api/v1/sessions")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, tokenService.issueToken("student-bob"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_TOPIC"));
    }

    @Test
    void testStartSession_BlankTopic_Returns400ValidationError() throws Exception {
        String payload = """
                {
                    "topicId": ""
                }
                """;

        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void testQuestionAndAnswerFlow_AdvancesStage() throws Exception {
        // 1. Start Session
        String startPayload = """
                {
                    "topicId": "linear-search",
                    "learnerId": "student-charlie"
                }
                """;
        MvcResult sessionResult = mockMvc.perform(post("/api/v1/sessions")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, tokenService.issueToken("student-charlie"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startPayload))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = objectMapper.readTree(sessionResult.getResponse().getContentAsString())
                .get("sessionId").asText();
        String token = sessionResult.getResponse().getHeader(SessionSecurityInterceptor.TOKEN_HEADER);

        // 2. Fetch current question
        MvcResult qResult = mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/current-question")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptId").isNotEmpty())
                .andExpect(jsonPath("$.stage").value("CONCEPT_ASSESSMENT"))
                .andExpect(jsonPath("$.content").isNotEmpty())
                .andReturn();

        String attemptId = objectMapper.readTree(qResult.getResponse().getContentAsString())
                .get("attemptId").asText();

        // 3. Submit correct answer
        String submitPayload = String.format("""
                {
                    "attemptId": "%s",
                    "answer": "Binary search divides the search space in half each step."
                }
                """, attemptId);

        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/submit-answer")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptId").value(attemptId))
                .andExpect(jsonPath("$.evaluation").value("PASS"))
                .andExpect(jsonPath("$.stageAdvanced").value(true));

        // 4. Fetch next question and verify stage advanced to DERIVE
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/current-question")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptId").isString())
                .andExpect(jsonPath("$.stage").value("DERIVE"));
    }

    @Test
    void testTwoIndependentSessions_RemainIsolated() throws Exception {
        // Session A
        MvcResult resA = mockMvc.perform(post("/api/v1/sessions")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, tokenService.issueToken("learner-a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\": \"linear-search\", \"learnerId\": \"learner-a\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String sessionA = objectMapper.readTree(resA.getResponse().getContentAsString()).get("sessionId").asText();
        String tokenA = resA.getResponse().getHeader(SessionSecurityInterceptor.TOKEN_HEADER);

        // Session B
        MvcResult resB = mockMvc.perform(post("/api/v1/sessions")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, tokenService.issueToken("learner-b"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\": \"binary-search\", \"learnerId\": \"learner-b\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String sessionB = objectMapper.readTree(resB.getResponse().getContentAsString()).get("sessionId").asText();
        String tokenB = resB.getResponse().getHeader(SessionSecurityInterceptor.TOKEN_HEADER);

        // Advance Session A
        MvcResult qA = mockMvc.perform(get("/api/v1/sessions/" + sessionA + "/current-question")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, tokenA))
                .andExpect(status().isOk())
                .andReturn();
        String attemptA = objectMapper.readTree(qA.getResponse().getContentAsString()).get("attemptId").asText();

        mockMvc.perform(post("/api/v1/sessions/" + sessionA + "/submit-answer")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"attemptId\": \"%s\", \"answer\": \"check items in sequence\"}", attemptA)))
                .andExpect(status().isOk());

        // Check Session B is still at initial state
        mockMvc.perform(get("/api/v1/sessions/" + sessionB)
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState").value("CONTENT_INTRO"))
                .andExpect(jsonPath("$.topicId").value("binary-search"))
                .andExpect(jsonPath("$.learnerId").value("learner-b"));
    }

    @Test
    void testDuplicateAttempt_RejectedWith409Conflict() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\": \"linear-search\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String sessionId = objectMapper.readTree(res.getResponse().getContentAsString()).get("sessionId").asText();
        String token = res.getResponse().getHeader(SessionSecurityInterceptor.TOKEN_HEADER);

        MvcResult q = mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/current-question")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andReturn();
        String attemptId = objectMapper.readTree(q.getResponse().getContentAsString()).get("attemptId").asText();

        String payload = String.format("{\"attemptId\": \"%s\", \"answer\": \"linear scan\"}", attemptId);

        // First submission succeeds
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/submit-answer")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        // Second submission of the exact same attempt is rejected with 409
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/submit-answer")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_ATTEMPT"));
    }

    @Test
    void testStaleOrMismatchAttempt_RejectedWith400() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\": \"linear-search\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String sessionId = objectMapper.readTree(res.getResponse().getContentAsString()).get("sessionId").asText();
        String token = res.getResponse().getHeader(SessionSecurityInterceptor.TOKEN_HEADER);

        // Submit with non-matching attempt ID
        String payload = "{\"attemptId\": \"arbitrary-fake-attempt-id\", \"answer\": \"any answer\"}";

        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/submit-answer")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_ATTEMPT"));
    }

    @Test
    void testConcurrentSubmissions_ProtectedFromDoubleAdvancement() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\": \"binary-search\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String sessionId = objectMapper.readTree(res.getResponse().getContentAsString()).get("sessionId").asText();
        String token = res.getResponse().getHeader(SessionSecurityInterceptor.TOKEN_HEADER);

        MvcResult q = mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/current-question")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andReturn();
        String attemptId = objectMapper.readTree(q.getResponse().getContentAsString()).get("attemptId").asText();

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        String payload = String.format("{\"attemptId\": \"%s\", \"answer\": \"concurrency test answer\"}", attemptId);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    var action = mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/submit-answer")
                            .header(SessionSecurityInterceptor.TOKEN_HEADER, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload));

                    int status = action.andReturn().getResponse().getStatus();
                    if (status == 200) {
                        successCount.incrementAndGet();
                    } else if (status == 409) {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // unexpected
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        finishLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one concurrent submission must succeed");
        assertEquals(threadCount - 1, conflictCount.get(), "All other concurrent submissions must be rejected with 409 Conflict");
    }

    @Test
    void testRequestHint_IncrementsServerTrackedHintCount() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\": \"binary-search\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String sessionId = objectMapper.readTree(res.getResponse().getContentAsString()).get("sessionId").asText();
        String token = res.getResponse().getHeader(SessionSecurityInterceptor.TOKEN_HEADER);

        MvcResult q = mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/current-question")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andReturn();
        String attemptId = objectMapper.readTree(q.getResponse().getContentAsString()).get("attemptId").asText();

        // Request hint 1
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/request-hint")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"attemptId\": \"%s\"}", attemptId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hintLevel").value(1))
                .andExpect(jsonPath("$.hintText").isNotEmpty());

        // Request hint 2
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/request-hint")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"attemptId\": \"%s\"}", attemptId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hintLevel").value(2));

        // Submit answer
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/submit-answer")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"attemptId\": \"%s\", \"answer\": \"answer with hints\"}", attemptId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hintsUsed").value(2));
    }

    @Test
    void testProviderFailureAndTimeout_DoesNotPenalizeLearner() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\": \"binary-search\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String sessionId = objectMapper.readTree(res.getResponse().getContentAsString()).get("sessionId").asText();
        String token = res.getResponse().getHeader(SessionSecurityInterceptor.TOKEN_HEADER);

        MvcResult q = mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/current-question")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andReturn();
        String attemptId = objectMapper.readTree(q.getResponse().getContentAsString()).get("attemptId").asText();

        // 1. Simulate provider failure
        if (llmProvider instanceof MockLLMProvider mock) {
            mock.setSimulateFailure(true);
        }

        try {
            // Submitting when AI fails returns 503 Service Unavailable with retryable: true
            mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/submit-answer")
                            .header(SessionSecurityInterceptor.TOKEN_HEADER, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format("{\"attemptId\": \"%s\", \"answer\": \"any answer\"}", attemptId)))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error").value("PROVIDER_UNAVAILABLE"))
                    .andExpect(jsonPath("$.retryable").value(true));

            // Verify session state was not penalized (no failure recorded, state unchanged)
            mockMvc.perform(get("/api/v1/sessions/" + sessionId)
                            .header(SessionSecurityInterceptor.TOKEN_HEADER, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mastery").value("NOVICE"))
                    .andExpect(jsonPath("$.currentState").value("CONCEPT_ASSESSMENT"));

            // 2. Recovery: unblock provider and retry the EXACT same attemptId
            if (llmProvider instanceof MockLLMProvider mock) {
                mock.setSimulateFailure(false);
                mock.setCannedResponse(String.class, "{\"evaluation\": \"PASS\", \"confidenceScore\": 0, \"feedback\": \"Recovered after transient failure!\"}");
            }

            mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/submit-answer")
                            .header(SessionSecurityInterceptor.TOKEN_HEADER, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format("{\"attemptId\": \"%s\", \"answer\": \"retry answer\"}", attemptId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.evaluation").value("PASS"))
                    .andExpect(jsonPath("$.stageAdvanced").value(true));
        } finally {
            if (llmProvider instanceof MockLLMProvider mock) {
                mock.setSimulateFailure(false);
            }
        }
    }

    @Test
    void testInvalidClientFields_CannotOverrideAuthoritativeState() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\": \"linear-search\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String sessionId = objectMapper.readTree(res.getResponse().getContentAsString()).get("sessionId").asText();
        String token = res.getResponse().getHeader(SessionSecurityInterceptor.TOKEN_HEADER);

        MvcResult q = mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/current-question")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andReturn();
        String attemptId = objectMapper.readTree(q.getResponse().getContentAsString()).get("attemptId").asText();

        // Attempting to send forged mastery, forged hint count, forged stage advancement
        String forgedPayload = String.format("""
                {
                    "attemptId": "%s",
                    "answer": "linear check",
                    "mastery": "MASTERED",
                    "evaluation": "PASS",
                    "hintsUsed": 0,
                    "stageAdvanced": true
                }
                """, attemptId);

        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/submit-answer")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(forgedPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentMastery").value("NOVICE")); // Forged "MASTERED" was safely ignored
    }

    @Test
    void testNonExistentSession_Returns404ForEndpoints() throws Exception {
        String nonExistent = "00000000-0000-0000-0000-000000000000";
        String token = tokenService.issueToken("probe-learner");

        // GET /sessions/{id}
        mockMvc.perform(get("/api/v1/sessions/" + nonExistent)
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("SESSION_NOT_FOUND"));

        // GET /sessions/{id}/current-question
        mockMvc.perform(get("/api/v1/sessions/" + nonExistent + "/current-question")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("SESSION_NOT_FOUND"));

        // POST /sessions/{id}/request-hint
        mockMvc.perform(post("/api/v1/sessions/" + nonExistent + "/request-hint")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attemptId\": \"some-attempt\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("SESSION_NOT_FOUND"));

        // POST /sessions/{id}/submit-answer
        mockMvc.perform(post("/api/v1/sessions/" + nonExistent + "/submit-answer")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attemptId\": \"some-attempt\", \"answer\": \"any\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("SESSION_NOT_FOUND"));
    }

    @Test
    void testGetCurrentQuestion_IsIdempotent() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\": \"linear-search\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String sessionId = objectMapper.readTree(res.getResponse().getContentAsString()).get("sessionId").asText();
        String token = res.getResponse().getHeader(SessionSecurityInterceptor.TOKEN_HEADER);

        MvcResult q1 = mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/current-question")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andReturn();
        String attemptId1 = objectMapper.readTree(q1.getResponse().getContentAsString()).get("attemptId").asText();

        MvcResult q2 = mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/current-question")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andReturn();
        String attemptId2 = objectMapper.readTree(q2.getResponse().getContentAsString()).get("attemptId").asText();

        assertEquals(attemptId1, attemptId2, "Repeated calls to current-question must return the same active attempt");
    }

    @Test
    void testSubmitAnswer_BeforeFetchingQuestion_Returns400() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\": \"linear-search\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String sessionId = objectMapper.readTree(res.getResponse().getContentAsString()).get("sessionId").asText();
        String token = res.getResponse().getHeader(SessionSecurityInterceptor.TOKEN_HEADER);

        // Submitting before current-question is ever vended
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/submit-answer")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attemptId\": \"arbitrary-id\", \"answer\": \"my answer\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_ATTEMPT"));
    }

    @Test
    void testIncorrectAnswer_TransitionsToRemediation() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\": \"linear-search\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String sessionId = objectMapper.readTree(res.getResponse().getContentAsString()).get("sessionId").asText();
        String token = res.getResponse().getHeader(SessionSecurityInterceptor.TOKEN_HEADER);

        MvcResult q = mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/current-question")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andReturn();
        String attemptId = objectMapper.readTree(q.getResponse().getContentAsString()).get("attemptId").asText();

        // Simulate incorrect answer evaluated as FAIL
        if (llmProvider instanceof MockLLMProvider mock) {
            mock.setCannedResponse(String.class, "{\"evaluation\": \"FAIL\", \"confidenceScore\": 0, \"feedback\": \"Incorrect answer.\"}");
        }

        try {
            mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/submit-answer")
                            .header(SessionSecurityInterceptor.TOKEN_HEADER, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format("{\"attemptId\": \"%s\", \"answer\": \"completely wrong answer\"}", attemptId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.evaluation").value("FAIL"))
                    .andExpect(jsonPath("$.stageAdvanced").value(false))
                    .andExpect(jsonPath("$.currentState").value("REMEDIATION"));
        } finally {
            if (llmProvider instanceof MockLLMProvider mock) {
                mock.setCannedResponse(String.class, "{\"evaluation\": \"PASS\", \"confidenceScore\": 0, \"feedback\": \"Correct answer verified by deterministic mock.\"}");
            }
        }
    }

    @Test
    void testClientClaimingCodeTestsPassed_WithoutTrustedServerVerification_CannotPassOrAdvance() throws Exception {
        // 1. Start session
        MvcResult res = mockMvc.perform(post("/api/v1/sessions")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, tokenService.issueToken("untrusted-runner-client"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\": \"linear-search\", \"learnerId\": \"untrusted-runner-client\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String sessionId = objectMapper.readTree(res.getResponse().getContentAsString()).get("sessionId").asText();
        String token = res.getResponse().getHeader(SessionSecurityInterceptor.TOKEN_HEADER);

        // 2. Advance through CONCEPT_ASSESSMENT
        MvcResult q1 = mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/current-question")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("CONCEPT_ASSESSMENT"))
                .andReturn();
        String attempt1 = objectMapper.readTree(q1.getResponse().getContentAsString()).get("attemptId").asText();

        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/submit-answer")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"attemptId\": \"%s\", \"answer\": \"any concept answer\"}", attempt1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluation").value("PASS"))
                .andExpect(jsonPath("$.stageAdvanced").value(true));

        // 3. Advance through DERIVE
        MvcResult q2 = mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/current-question")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("DERIVE"))
                .andReturn();
        String attempt2 = objectMapper.readTree(q2.getResponse().getContentAsString()).get("attemptId").asText();

        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/submit-answer")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"attemptId\": \"%s\", \"answer\": \"derivation steps\"}", attempt2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluation").value("PASS"))
                .andExpect(jsonPath("$.stageAdvanced").value(true));

        // 4. Arrive at IMPLEMENTATION stage
        MvcResult q3 = mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/current-question")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("IMPLEMENTATION"))
                .andReturn();
        String attempt3 = objectMapper.readTree(q3.getResponse().getContentAsString()).get("attemptId").asText();

        // 5. Client submits implementation claiming "codeTestsPassed": true
        // Even though AI evaluator returns PASS, client claim cannot establish deterministic verification
        String clientClaimPayload = String.format("""
                {
                    "attemptId": "%s",
                    "answer": "public int linearSearch(int[] arr, int target) { return 0; }",
                    "codeTestsPassed": true
                }
                """, attempt3);

        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/submit-answer")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientClaimPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluation").value("FAIL")) // Forced to FAIL due to unverified tests
                .andExpect(jsonPath("$.stageAdvanced").value(false))
                .andExpect(jsonPath("$.currentState").value("DEBUGGING")) // Drops to DEBUGGING, not EXPLANATION
                .andExpect(jsonPath("$.currentMastery").value("FAMILIAR")); // Mastery stays at FAMILIAR, does not advance to PROFICIENT
    }
}
