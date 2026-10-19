package com.javadsamastery.session;

import com.javadsamastery.ai.mock.MockLLMProvider;
import com.javadsamastery.ai.provider.LLMProvider;
import com.javadsamastery.domain.content.QuestionBank;
import com.javadsamastery.domain.curriculum.CurriculumLoader;
import com.javadsamastery.domain.topic.CurriculumGraph;
import com.javadsamastery.dto.SessionResponse;
import com.javadsamastery.engine.AITutorEngine;
import com.javadsamastery.engine.MasteryEngine;
import com.javadsamastery.engine.QuestionSelector;
import com.javadsamastery.service.SessionOrchestratorService;
import com.javadsamastery.service.SessionReaperService;
import com.javadsamastery.service.exception.SessionExpiredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SessionExpirationAndReaperTest {

    private CurriculumGraph curriculumGraph;
    private QuestionBank questionBank;
    private QuestionSelector questionSelector;
    private MasteryEngine masteryEngine;
    private AITutorEngine tutorEngine;
    private LLMProvider llmProvider;

    @BeforeEach
    void setUp() {
        curriculumGraph = CurriculumLoader.loadCurriculum("/curriculum/curriculum.json");
        questionBank = new QuestionBank();
        questionBank.loadFromJsonResource("/questions/binary-search.json");
        questionBank.loadFromJsonResource("/questions/linear-search.json");
        questionSelector = new QuestionSelector(questionBank);
        masteryEngine = new MasteryEngine();
        llmProvider = new MockLLMProvider();
        tutorEngine = new AITutorEngine(null, null, null);
    }

    @Test
    @DisplayName("RES-02: Expired session throws SessionExpiredException on all access paths")
    void testExpiredSession_ThrowsSessionExpiredException() {
        // Configure TTL of 0 hours (expires immediately)
        SessionOrchestratorService service = new SessionOrchestratorService(
                curriculumGraph, questionBank, questionSelector, masteryEngine, tutorEngine, llmProvider,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                1000L, 30L, 0L
        );

        SessionResponse session = service.startSession("binary-search", "learner-expired");
        String sessionId = session.sessionId();

        // Any access to expired session must throw SessionExpiredException
        assertThrows(SessionExpiredException.class, () -> service.getSession(sessionId));
        assertThrows(SessionExpiredException.class, () -> service.getCurrentQuestion(sessionId));
        assertThrows(SessionExpiredException.class, () -> service.requestHint(sessionId, "dummy-attempt"));
        assertThrows(SessionExpiredException.class, () -> service.submitAnswer(sessionId, "dummy-attempt", "ans", true));
    }

    @Test
    @DisplayName("RES-02: Boundary time validation - session valid before expiresAt, expired after expiresAt")
    void testBoundaryTimeValidation() {
        // Configure TTL = 24 hours
        SessionOrchestratorService service = new SessionOrchestratorService(
                curriculumGraph, questionBank, questionSelector, masteryEngine, tutorEngine, llmProvider,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                1000L, 30L, 24L
        );

        SessionResponse session = service.startSession("binary-search", "learner-boundary");
        String sessionId = session.sessionId();

        // Valid immediately
        assertDoesNotThrow(() -> service.getSession(sessionId));

        // Test reaper with time 23 hours in future (not expired yet)
        SessionReaperService reaper = new SessionReaperService(Optional.empty(), Optional.empty(), service);
        SessionReaperService.ReaperResult r1 = reaper.reapExpiredSessions(Instant.now().plus(23, ChronoUnit.HOURS));
        assertEquals(0, r1.cachedSessionsEvicted(), "Session must not be reaped before expiresAt");
        assertDoesNotThrow(() -> service.getSession(sessionId));

        // Test reaper with time 25 hours in future (now expired)
        SessionReaperService.ReaperResult r2 = reaper.reapExpiredSessions(Instant.now().plus(25, ChronoUnit.HOURS));
        assertEquals(1, r2.cachedSessionsEvicted(), "Session must be reaped when past expiresAt");

        // After eviction from reaper, session is gone
        assertThrows(Exception.class, () -> service.getSession(sessionId));
    }

    @Test
    @DisplayName("RES-02: Distinguishes successful completion from expiration")
    void testDistinguishCompletionFromExpiration() {
        SessionOrchestratorService service = new SessionOrchestratorService(
                curriculumGraph, questionBank, questionSelector, masteryEngine, tutorEngine, llmProvider
        );

        SessionResponse session = service.startSession("linear-search", "learner-comp");
        assertFalse(session.completed(), "Newly started session is not completed");

        // Calling getSession returns completed = false without error
        SessionResponse current = service.getSession(session.sessionId());
        assertFalse(current.completed());
        assertEquals("CONTENT_INTRO", current.currentState());
    }

    @Test
    @DisplayName("RES-02: In-memory reaper is idempotent")
    void testReaperIdempotence() {
        SessionOrchestratorService service = new SessionOrchestratorService(
                curriculumGraph, questionBank, questionSelector, masteryEngine, tutorEngine, llmProvider,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                1000L, 30L, 1L // 1 hour TTL
        );

        service.startSession("binary-search", "learner-1");
        service.startSession("binary-search", "learner-2");

        SessionReaperService reaper = new SessionReaperService(Optional.empty(), Optional.empty(), service);

        // Run reaper at T + 2 hours: sweeps both sessions
        Instant futureTime = Instant.now().plus(2, ChronoUnit.HOURS);
        SessionReaperService.ReaperResult r1 = reaper.reapExpiredSessions(futureTime);
        assertEquals(2, r1.cachedSessionsEvicted());

        // Run reaper second time at same future time: sweeps 0 sessions (idempotent)
        SessionReaperService.ReaperResult r2 = reaper.reapExpiredSessions(futureTime);
        assertEquals(0, r2.cachedSessionsEvicted());
    }
}
