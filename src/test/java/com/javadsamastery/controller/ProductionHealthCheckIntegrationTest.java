package com.javadsamastery.controller;

import com.javadsamastery.JavaDsaMasteryApplication;
import com.javadsamastery.security.LearnerTokenService;
import com.javadsamastery.security.SessionSecurityInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = JavaDsaMasteryApplication.class)
@AutoConfigureMockMvc
class ProductionHealthCheckIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LearnerTokenService tokenService;

    @Test
    @DisplayName("OPS-03: /actuator/health returns status UP with minimum details")
    void testActuatorHealth_ReturnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                // Ensure sensitive internal details (diskSpace, db host, env) are not leaked
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    @DisplayName("OPS-03: /actuator/health/liveness probe returns UP")
    void testActuatorLiveness_ReturnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));
    }

    @Test
    @DisplayName("OPS-03: /actuator/health/readiness probe returns UP")
    void testActuatorReadiness_ReturnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));
    }

    @Test
    @DisplayName("OPS-03: Unexposed Actuator endpoints return 404 NOT FOUND")
    void testUnexposedActuatorEndpoints_Return404() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/actuator/beans"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/actuator/configprops"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/actuator/mappings"))
                .andExpect(status().isNotFound());
    }

    @Autowired
    private com.javadsamastery.service.SessionOrchestratorService sessionService;

    @Test
    @DisplayName("OPS-03: /api/v1/health custom endpoint returns UP with active topics")
    void testApiHealth_ReturnsUp() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.activeTopics", is(7)));
    }

    @Test
    @DisplayName("RES-02: Accessing expired session via REST API returns HTTP 410 GONE with SESSION_EXPIRED")
    void testExpiredSession_ReturnsHttp410Gone() throws Exception {
        // 1. Create a session
        String startJson = "{\"topicId\": \"binary-search\"}";
        var result = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startJson))
                .andExpect(status().isCreated())
                .andReturn();

        String token = result.getResponse().getHeader(SessionSecurityInterceptor.TOKEN_HEADER);
        String responseBody = result.getResponse().getContentAsString();
        String sessionId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(responseBody).get("sessionId").asText();

        // 2. Simulate session expiration
        sessionService.expireSessionForTesting(sessionId);

        // 3. Any API access to the expired session must return HTTP 410 GONE
        mockMvc.perform(get("/api/v1/sessions/" + sessionId)
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error", is("SESSION_EXPIRED")))
                .andExpect(jsonPath("$.retryable", is(false)));

        // 4. Requesting question on expired session must also return HTTP 410 GONE
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/current-question")
                        .header(SessionSecurityInterceptor.TOKEN_HEADER, token))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error", is("SESSION_EXPIRED")))
                .andExpect(jsonPath("$.retryable", is(false)));
    }
}
