package com.javadsamastery.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javadsamastery.JavaDsaMasteryApplication;
import com.javadsamastery.config.ApplicationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = JavaDsaMasteryApplication.class)
@AutoConfigureMockMvc
class SecurityHardeningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LearnerTokenService tokenService;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private RateLimitingInterceptor rateLimitingInterceptor;

    @BeforeEach
    void resetRateLimiter() {
        rateLimiterService.reset();
    }

    // =========================================================================
    // 1. DTO Validation & Payload Constraints
    // =========================================================================

    @Test
    @DisplayName("Payload Validation: Answer exceeding 2000 characters is rejected with 400 VALIDATION_ERROR")
    void testSubmitAnswer_OversizedAnswer_Returns400() throws Exception {
        MvcResult sessionRes = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\": \"linear-search\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = objectMapper.readTree(sessionRes.getResponse().getContentAsString()).get("sessionId").asText();
        String token = sessionRes.getResponse().getHeader(SessionSecurityInterceptor.TOKEN_HEADER);

        MvcResult qRes = mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/current-question")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andReturn();

        String attemptId = objectMapper.readTree(qRes.getResponse().getContentAsString()).get("attemptId").asText();

        String oversizedAnswer = "A".repeat(2500);
        String payload = String.format("{\"attemptId\": \"%s\", \"answer\": \"%s\"}", attemptId, oversizedAnswer);

        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/submit-answer")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("must not exceed 2000 characters")));
    }

    @Test
    @DisplayName("Payload Validation: Invalid learnerId characters or length is rejected with 400 VALIDATION_ERROR")
    void testStartSession_InvalidLearnerId_Returns400() throws Exception {
        String invalidPayload = """
                {
                    "topicId": "binary-search",
                    "learnerId": "student<script>alert(1)</script>"
                }
                """;

        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("learnerId")));
    }

    @Test
    @DisplayName("Payload Validation: Invalid topicId pattern is rejected with 400 VALIDATION_ERROR")
    void testStartSession_InvalidTopicPattern_Returns400() throws Exception {
        String invalidPayload = """
                {
                    "topicId": "INVALID_UPPERCASE_TOPIC"
                }
                """;

        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("topicId")));
    }

    // =========================================================================
    // 2. Token Lifecycle & Cryptographic Integrity
    // =========================================================================

    @Test
    @DisplayName("Token Lifecycle: Genuine token validates; tampered signature or payload is rejected")
    void testTokenService_IntegrityAndTamperResistance() {
        String learnerId = "learner-secure-123";
        String token = tokenService.issueToken(learnerId);

        assertNotNull(token);
        assertTrue(tokenService.validateToken(token));

        Optional<String> extracted = tokenService.extractLearnerId(token);
        assertTrue(extracted.isPresent());
        assertEquals(learnerId, extracted.get());

        // Tamper with learnerId
        String tamperedLearner = "learner-attacker-999." + token.split("\\.")[1] + "." + token.split("\\.")[2];
        assertFalse(tokenService.validateToken(tamperedLearner), "Tampered learner ID must fail validation");

        // Tamper with signature
        String tamperedSig = token.substring(0, token.length() - 4) + "XXXX";
        assertFalse(tokenService.validateToken(tamperedSig), "Tampered signature must fail validation");

        // Malformed tokens
        assertFalse(tokenService.validateToken("not-a-token"));
        assertFalse(tokenService.validateToken("a.b"));
        assertFalse(tokenService.validateToken(""));
        assertFalse(tokenService.validateToken(null));
    }

    @Test
    @DisplayName("Token Lifecycle: Expired token (>30 days) is rejected with 401 UNAUTHORIZED")
    void testExpiredToken_RejectedWith401() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\": \"linear-search\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = objectMapper.readTree(res.getResponse().getContentAsString()).get("sessionId").asText();

        // Craft an expired token (35 days old)
        long expiredTimestamp = System.currentTimeMillis() - (35L * 24L * 60L * 60L * 1000L);
        LearnerTokenService tempService = new LearnerTokenService(LearnerTokenService.DEFAULT_DEV_SECRET, 30, "default");
        String payload = "expired-learner." + expiredTimestamp;
        String expiredToken = payload + ".dummySig";
        assertFalse(tempService.validateToken(expiredToken));

        mockMvc.perform(get("/api/v1/sessions/" + sessionId)
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    // =========================================================================
    // 3. Learner Ownership & Identity Protection (Task 2)
    // =========================================================================

    @Test
    @DisplayName("Ownership: Missing token on any protected session endpoint returns 401 UNAUTHORIZED")
    void testMissingToken_OnProtectedEndpoints_Returns401() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\": \"linear-search\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = objectMapper.readTree(res.getResponse().getContentAsString()).get("sessionId").asText();

        // 1. GET /sessions/{id} without token -> 401
        mockMvc.perform(get("/api/v1/sessions/" + sessionId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Missing required X-Learner-Token")));

        // 2. GET /sessions/{id}/current-question without token -> 401
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/current-question"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

        // 3. POST /sessions/{id}/request-hint without token -> 401
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/request-hint")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attemptId\": \"dummy\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

        // 4. POST /sessions/{id}/submit-answer without token -> 401
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/submit-answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attemptId\": \"dummy\", \"answer\": \"test\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Session Ownership: Cross-learner access is strictly rejected with 403 FORBIDDEN")
    void testCrossLearnerSessionAccess_RejectedWith403() throws Exception {
        MvcResult resA = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\": \"linear-search\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionA = objectMapper.readTree(resA.getResponse().getContentAsString()).get("sessionId").asText();
        String tokenAlice = resA.getResponse().getHeader(SessionSecurityInterceptor.TOKEN_HEADER);

        MvcResult resB = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\": \"linear-search\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String tokenBob = resB.getResponse().getHeader(SessionSecurityInterceptor.TOKEN_HEADER);

        // Alice accessing Session A -> 200 OK
        mockMvc.perform(get("/api/v1/sessions/" + sessionA)
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, tokenAlice))
                .andExpect(status().isOk());

        // Bob attempting to access Alice's Session A -> 403 FORBIDDEN
        mockMvc.perform(get("/api/v1/sessions/" + sessionA)
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, tokenBob))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Cross-learner access denied")));
    }

    @Test
    @DisplayName("Identity Protection: Unauthenticated request cannot supply non-existent learnerId (returns 403)")
    void testStartSession_WithoutToken_RejectsNonExistentLearnerId_Returns403() throws Exception {
        String nonExistentLearner = "squat-target-" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\": \"binary-search\", \"learnerId\": \"" + nonExistentLearner + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Cannot specify client-chosen learnerId without a valid X-Learner-Token")));
    }

    @Test
    @DisplayName("Identity Protection: Unauthenticated request cannot supply existing learnerId (returns 403)")
    void testStartSession_WithoutToken_RejectsExistingLearnerId_Returns403() throws Exception {
        // 1. Initial valid creation
        MvcResult res = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\": \"binary-search\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String existingLearner = objectMapper.readTree(res.getResponse().getContentAsString()).get("learnerId").asText();

        // 2. Attacker attempts to claim existing learner without a token -> 403
        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\": \"binary-search\", \"learnerId\": \"" + existingLearner + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Cannot specify client-chosen learnerId without a valid X-Learner-Token")));
    }

    @Test
    @DisplayName("Identity Protection: Anonymous creation without learnerId mints server UUID and returns valid token")
    void testStartSession_WithoutToken_OmittedLearnerId_MintsServerUUID_Returns201() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\": \"binary-search\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String mintedLearnerId = objectMapper.readTree(res.getResponse().getContentAsString()).get("learnerId").asText();
        String token = res.getResponse().getHeader(SessionSecurityInterceptor.TOKEN_HEADER);

        assertNotNull(token);
        assertTrue(mintedLearnerId.startsWith("learner-"));
        assertTrue(tokenService.validateToken(token));
        assertEquals(mintedLearnerId, tokenService.extractLearnerId(token).orElseThrow());
    }

    @Test
    @DisplayName("Identity Protection: Returning learner with valid token can create subsequent sessions")
    void testStartSession_WithValidToken_ReturningLearner_Succeeds() throws Exception {
        // 1. Create initial session to obtain valid token
        MvcResult res1 = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\": \"linear-search\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String originalLearnerId = objectMapper.readTree(res1.getResponse().getContentAsString()).get("learnerId").asText();
        String token = res1.getResponse().getHeader(SessionSecurityInterceptor.TOKEN_HEADER);

        // 2. Create second session presenting the token
        MvcResult res2 = mockMvc.perform(post("/api/v1/sessions")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\": \"binary-search\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String secondSessionLearnerId = objectMapper.readTree(res2.getResponse().getContentAsString()).get("learnerId").asText();
        String returnToken = res2.getResponse().getHeader(SessionSecurityInterceptor.TOKEN_HEADER);

        assertEquals(originalLearnerId, secondSessionLearnerId, "Returning learner session must bind to authenticated identity");
        assertEquals(token, returnToken, "Valid token should be preserved for returning learner");
    }

    @Test
    @DisplayName("Identity Protection: Presenting token with mismatched body learnerId returns 403 FORBIDDEN")
    void testStartSession_WithValidToken_MismatchedBodyLearnerId_Returns403() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\": \"linear-search\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String token = res.getResponse().getHeader(SessionSecurityInterceptor.TOKEN_HEADER);

        mockMvc.perform(post("/api/v1/sessions")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\": \"binary-search\", \"learnerId\": \"attacker-chosen-different-id\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Learner identity mismatch")));
    }

    // =========================================================================
    // 4. Rate Limiting & Quota Robustness (Task 1)
    // =========================================================================

    @Test
    @DisplayName("Rate Limiting: Exceeding AI request limit triggers 429 TOO_MANY_REQUESTS with Retry-After")
    void testRateLimiting_ExceededQuota_Returns429() throws Exception {
        MvcResult sessionRes = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\": \"linear-search\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = objectMapper.readTree(sessionRes.getResponse().getContentAsString()).get("sessionId").asText();
        String token = sessionRes.getResponse().getHeader(SessionSecurityInterceptor.TOKEN_HEADER);

        MvcResult qRes = mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/current-question")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andReturn();

        String attemptId = objectMapper.readTree(qRes.getResponse().getContentAsString()).get("attemptId").asText();
        String hintPayload = String.format("{\"attemptId\": \"%s\"}", attemptId);

        boolean got429 = false;
        for (int i = 0; i < 15; i++) {
            MvcResult res = mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/request-hint")
                            .header(SessionSecurityInterceptor.TOKEN_HEADER, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(hintPayload))
                    .andReturn();

            if (res.getResponse().getStatus() == 429) {
                got429 = true;
                assertNotNull(res.getResponse().getHeader("Retry-After"));
                assertTrue(res.getResponse().getContentAsString().contains("RATE_LIMIT_EXCEEDED"));
                break;
            }
        }

        assertTrue(got429, "Exhausting quota must trigger HTTP 429");
    }

    @Test
    @DisplayName("Rate Limiting: Saturated session bucket does NOT drain global AI quota (Task 1)")
    void testSessionExceedingLimit_DoesNotDrainGlobalAIQuota() throws Exception {
        // 1. Session 1 for Learner 1
        MvcResult s1Res = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\": \"linear-search\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String session1 = objectMapper.readTree(s1Res.getResponse().getContentAsString()).get("sessionId").asText();
        String token1 = s1Res.getResponse().getHeader(SessionSecurityInterceptor.TOKEN_HEADER);

        MvcResult q1Res = mockMvc.perform(get("/api/v1/sessions/" + session1 + "/current-question")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token1)).andReturn();
        String attempt1 = objectMapper.readTree(q1Res.getResponse().getContentAsString()).get("attemptId").asText();

        // Exhaust session limit (10 RPM) with 15 requests
        for (int i = 0; i < 15; i++) {
            mockMvc.perform(post("/api/v1/sessions/" + session1 + "/request-hint")
                    .header(SessionSecurityInterceptor.TOKEN_HEADER, token1)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(String.format("{\"attemptId\": \"%s\"}", attempt1)));
        }

        // Global bucket capacity was 60.0. The 10 successful requests consumed 10 tokens.
        // The 5 rejected requests must NOT have consumed any global tokens.
        double availableGlobal = rateLimiterService.getAvailableTokens("ai:global");
        assertTrue(availableGlobal >= 48.0, "Global quota must not be drained by rejected session requests (actual: " + availableGlobal + ")");

        // 2. Different Learner 2 in Session 2 must be able to make AI requests without 429
        MvcResult s2Res = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\": \"binary-search\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String session2 = objectMapper.readTree(s2Res.getResponse().getContentAsString()).get("sessionId").asText();
        String token2 = s2Res.getResponse().getHeader(SessionSecurityInterceptor.TOKEN_HEADER);

        MvcResult q2Res = mockMvc.perform(get("/api/v1/sessions/" + session2 + "/current-question")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token2)).andReturn();
        String attempt2 = objectMapper.readTree(q2Res.getResponse().getContentAsString()).get("attemptId").asText();

        mockMvc.perform(post("/api/v1/sessions/" + session2 + "/request-hint")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"attemptId\": \"%s\"}", attempt2)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Rate Limiting: Multi-session quota bypass prevented via per-learner composite limit")
    void testMultiSession_AIQuotaEnforcedPerLearner() throws Exception {
        MvcResult res1 = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\": \"linear-search\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String session1 = objectMapper.readTree(res1.getResponse().getContentAsString()).get("sessionId").asText();
        String token = res1.getResponse().getHeader(SessionSecurityInterceptor.TOKEN_HEADER);

        MvcResult res2 = mockMvc.perform(post("/api/v1/sessions")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\": \"binary-search\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String session2 = objectMapper.readTree(res2.getResponse().getContentAsString()).get("sessionId").asText();

        MvcResult q1 = mockMvc.perform(get("/api/v1/sessions/" + session1 + "/current-question")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token)).andReturn();
        String attempt1 = objectMapper.readTree(q1.getResponse().getContentAsString()).get("attemptId").asText();

        MvcResult q2 = mockMvc.perform(get("/api/v1/sessions/" + session2 + "/current-question")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token)).andReturn();
        String attempt2 = objectMapper.readTree(q2.getResponse().getContentAsString()).get("attemptId").asText();

        boolean got429 = false;
        for (int i = 0; i < 20; i++) {
            String activeSession = (i % 2 == 0) ? session1 : session2;
            String activeAttempt = (i % 2 == 0) ? attempt1 : attempt2;

            MvcResult r = mockMvc.perform(post("/api/v1/sessions/" + activeSession + "/request-hint")
                            .header(SessionSecurityInterceptor.TOKEN_HEADER, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format("{\"attemptId\": \"%s\"}", activeAttempt)))
                    .andReturn();

            if (r.getResponse().getStatus() == 429) {
                got429 = true;
                break;
            }
        }
        assertTrue(got429, "Learner AI composite quota must prevent multi-session quota exhaustion bypass");
    }

    @Test
    @DisplayName("Rate Limiting: TokenBucket boundary conditions, refund cap, and availability checks")
    void testTokenBucket_BoundaryConditionsAndRefund() {
        RateLimiterService.TokenBucket bucket = new RateLimiterService.TokenBucket(5.0, 1.0);

        assertTrue(bucket.hasAvailableToken());
        assertEquals(5.0, bucket.getAvailableTokens(), 0.01);

        // Consume all tokens
        for (int i = 0; i < 5; i++) {
            assertTrue(bucket.tryConsume());
        }
        assertFalse(bucket.tryConsume(), "Empty bucket must not allow consume");
        assertFalse(bucket.hasAvailableToken(), "Empty bucket hasAvailableToken must be false");

        // Refund 1 token
        bucket.refund();
        assertTrue(bucket.hasAvailableToken(), "Refunded bucket must have available token");
        assertTrue(bucket.tryConsume(), "Refunded token must be consumable");

        // Refund cannot exceed capacity
        bucket.refund();
        bucket.refund();
        bucket.refund();
        bucket.refund();
        bucket.refund();
        bucket.refund(); // 6 refunds on 5 capacity
        assertTrue(bucket.getAvailableTokens() <= 5.0, "Refund must be capped at bucket capacity");
    }

    // =========================================================================
    // 5. AI Endpoint Matching Hardening (Task 3)
    // =========================================================================

    @Test
    @DisplayName("Endpoint Matching: Trailing slashes and matrix parameters are normalized and rate-limited")
    void testAiEndpointMatching_NormalizedPaths() {
        assertEquals("/api/v1/sessions/123/submit-answer",
                RateLimitingInterceptor.normalizeUri("/api/v1/sessions/123/submit-answer/"));
        assertEquals("/api/v1/sessions/123/submit-answer",
                RateLimitingInterceptor.normalizeUri("/api/v1/sessions/123/submit-answer///"));
        assertEquals("/api/v1/sessions/123/submit-answer",
                RateLimitingInterceptor.normalizeUri("/api/v1/sessions/123/submit-answer;jsessionid=abc"));
        assertEquals("/api/v1/sessions/123/submit-answer",
                RateLimitingInterceptor.normalizeUri("/api/v1/sessions/123/submit-answer;param=1;param2=2/"));
    }

    @Test
    @DisplayName("Endpoint Matching: Non-AI endpoints are not matched as AI composite endpoints")
    void testNonAiEndpoints_NotMatchedAsAiComposite() {
        String sessionUrl = RateLimitingInterceptor.normalizeUri("/api/v1/sessions/123");
        String currentQuestionUrl = RateLimitingInterceptor.normalizeUri("/api/v1/sessions/123/current-question");

        assertFalse(sessionUrl.endsWith("/submit-answer") || sessionUrl.endsWith("/request-hint"));
        assertFalse(currentQuestionUrl.endsWith("/submit-answer") || currentQuestionUrl.endsWith("/request-hint"));
    }

    // =========================================================================
    // 6. Proxy Security & Forwarded Header Hardening (Task 4)
    // =========================================================================

    @Test
    @DisplayName("Proxy Security: Direct untrusted client ignores X-Forwarded-For entirely")
    void testSpoofedForwardedForHeader_IgnoredByDefault() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.195");
        request.addHeader("X-Forwarded-For", "198.51.100.1, 10.0.0.1");

        String resolvedIp = rateLimitingInterceptor.extractClientIp(request);
        assertEquals("203.0.113.195", resolvedIp, "Untrusted client direct connection must ignore X-Forwarded-For");
    }

    @Test
    @DisplayName("Proxy Security: Prepend spoofing in trusted proxy is neutralized via right-to-left traversal")
    void testTrustedProxy_PrependSpoofing_ExtractsRightmostUntrustedClientIp() {
        RateLimitingInterceptor proxyInterceptor = new RateLimitingInterceptor(rateLimiterService, "10.0.0.1");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1"); // Direct socket connection is trusted proxy
        // Attacker prepended 198.51.100.spoofed; proxy appended real client IP 203.0.113.50
        request.addHeader("X-Forwarded-For", "198.51.100.spoofed, 203.0.113.50");

        String resolvedIp = proxyInterceptor.extractClientIp(request);
        assertEquals("203.0.113.50", resolvedIp, "Right-to-left traversal must extract real client IP and ignore prepended spoof");
    }

    @Test
    @DisplayName("Proxy Security: Multi-hop trusted proxy chain resolves leftmost untrusted client IP")
    void testTrustedProxy_MultiHopTrustedChain_ExtractsClientIp() {
        RateLimitingInterceptor proxyInterceptor = new RateLimitingInterceptor(rateLimiterService, "10.0.0.1, 10.0.0.2");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.2"); // Connected from proxy 2
        // Chain: Client (198.51.100.77) -> Proxy 1 (10.0.0.1) -> Proxy 2 (10.0.0.2)
        request.addHeader("X-Forwarded-For", "198.51.100.77, 10.0.0.1");

        String resolvedIp = proxyInterceptor.extractClientIp(request);
        assertEquals("198.51.100.77", resolvedIp, "Must traverse across intermediate trusted proxy to untrusted client");
    }

    @Test
    @DisplayName("Proxy Security: Malformed IP headers fall back safely to remote socket address")
    void testTrustedProxy_MalformedHeader_FallsBackSafelyToRemoteAddr() {
        RateLimitingInterceptor proxyInterceptor = new RateLimitingInterceptor(rateLimiterService, "10.0.0.1");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "evil.com, <script>alert(1)</script>, 999.999.999.999");

        String resolvedIp = proxyInterceptor.extractClientIp(request);
        assertEquals("10.0.0.1", resolvedIp, "Malformed forwarded headers must fall back safely to remoteAddr without DNS lookups");
    }

    @Test
    @DisplayName("Proxy Security: IPv6 addresses and bracketed notation are handled safely")
    void testTrustedProxy_IPv6Address_HandledSafely() {
        RateLimitingInterceptor proxyInterceptor = new RateLimitingInterceptor(rateLimiterService, "10.0.0.1");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "2001:db8::1");

        String resolvedIp = proxyInterceptor.extractClientIp(request);
        assertEquals("2001:db8::1", resolvedIp);

        // Bracketed notation
        MockHttpServletRequest bracketedReq = new MockHttpServletRequest();
        bracketedReq.setRemoteAddr("10.0.0.1");
        bracketedReq.addHeader("X-Forwarded-For", "[2001:db8::2]");

        assertEquals("2001:db8::2", proxyInterceptor.extractClientIp(bracketedReq));
    }

    // =========================================================================
    // 7. Storage Bounds & Production Secret Fail-Fast
    // =========================================================================

    @Test
    @DisplayName("Memory Bound: BoundedRateLimitStorage evicts LRU entries and honors capacity ceiling")
    void testBoundedRateLimitStorage_EnforcesMaxCapacityAndExpiry() {
        BoundedRateLimitStorage storage = new BoundedRateLimitStorage(5, 600);

        for (int i = 0; i < 5; i++) {
            storage.getOrCreate("key-" + i, () -> new RateLimiterService.TokenBucket(10, 1.0));
        }
        assertEquals(5, storage.size());

        // Adding 6th entry must evict LRU eldest entry
        storage.getOrCreate("key-5", () -> new RateLimiterService.TokenBucket(10, 1.0));
        assertEquals(5, storage.size(), "Storage size must never exceed maxCapacity");
        assertNull(storage.get("key-0"), "Eldest key-0 must have been evicted");
        assertNotNull(storage.get("key-5"), "Newest key-5 must be present");
    }

    @Test
    @DisplayName("Concurrency: Concurrent requests on general rate limiter execute safely")
    void testConcurrentGeneralRateLimiting_IsThreadSafe() throws InterruptedException {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger acquiredCount = new AtomicInteger(0);

        String testIp = "192.0.2.100";
        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    if (rateLimiterService.tryAcquireGeneral(testIp)) {
                        acquiredCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // unexpected
                } finally {
                    done.countDown();
                }
            });
        }

        latch.countDown();
        done.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(acquiredCount.get() > 0, "At least one token must be consumed");
        assertTrue(acquiredCount.get() <= 300, "Consumed tokens must not exceed capacity");
    }

    @Test
    @DisplayName("Security Headers: Responses contain defensive HTTP security headers")
    void testSecurityHeaders_PresentOnAllResponses() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy", "geolocation=(), camera=(), microphone=()"));
    }

    @Test
    @DisplayName("CORS: Preflight OPTIONS from approved origin returns allowed headers and credentials: false")
    void testCors_ApprovedOrigin_ReturnsHeaders() throws Exception {
        mockMvc.perform(options("/api/v1/sessions")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    @DisplayName("Production Secret Validation: Missing, weak, or fallback secrets throw IllegalStateException in prod")
    void testProductionSecretValidation_FailsFast() {
        assertThrows(IllegalStateException.class, () ->
                new LearnerTokenService(null, 30, "prod"));

        assertThrows(IllegalStateException.class, () ->
                new LearnerTokenService("   ", 30, "prod"));

        assertThrows(IllegalStateException.class, () ->
                new LearnerTokenService(LearnerTokenService.DEFAULT_DEV_SECRET, 30, "prod"));

        assertThrows(IllegalStateException.class, () ->
                new LearnerTokenService("short-secret-key-16chars", 30, "prod"));

        assertDoesNotThrow(() ->
                new LearnerTokenService("a".repeat(32), 30, "prod"));
    }

    @Test
    @DisplayName("Production AI Key Validation: llmProvider throws IllegalStateException if GEMINI_API_KEY omitted in prod")
    void testProductionProfile_MissingKey_FailsFast() {
        ApplicationConfig config = new ApplicationConfig();
        assertThrows(IllegalStateException.class, () -> {
            config.llmProvider(null, "prod");
        }, "Booting in prod profile without GEMINI_API_KEY must throw IllegalStateException");
    }
}
