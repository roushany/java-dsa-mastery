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
import com.javadsamastery.service.exception.SessionNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SessionCacheAndReliabilityTest {

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
    @DisplayName("RES-01: Bounded session cache enforces maximum size via LRU eviction")
    void testBoundedCache_EnforcesMaximumSize() {
        // Configure a small cache bound of 5 entries
        SessionOrchestratorService service = new SessionOrchestratorService(
                curriculumGraph, questionBank, questionSelector, masteryEngine, tutorEngine, llmProvider,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                5L, 30L, 24L
        );

        String firstSessionId = null;
        for (int i = 0; i < 6; i++) {
            SessionResponse resp = service.startSession("binary-search", "learner-" + i);
            if (i == 0) {
                firstSessionId = resp.sessionId();
            }
        }

        // Clean up Caffeine maintenance cycle
        service.cleanUpCache();

        // Total cached entries must not exceed max bound of 5
        assertTrue(service.getCachedSessionCount() <= 5, "Cached sessions must not exceed max-entries (5)");

        // The oldest session (firstSessionId) should be evicted from in-memory cache
        final String evictedId = firstSessionId;
        assertThrows(SessionNotFoundException.class, () -> service.getSession(evictedId),
                "Evicted session in non-persistent mode must throw SessionNotFoundException");
    }

    @Test
    @DisplayName("RES-01: Concurrent access to cached sessions is thread-safe")
    void testConcurrentAccess_ThreadSafe() throws InterruptedException {
        SessionOrchestratorService service = new SessionOrchestratorService(
                curriculumGraph, questionBank, questionSelector, masteryEngine, tutorEngine, llmProvider
        );

        SessionResponse session = service.startSession("binary-search", "learner-concurrent");
        String sessionId = session.sessionId();

        int threadCount = 16;
        int operationsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        SessionResponse resp = service.getSession(sessionId);
                        assertNotNull(resp);
                        assertEquals("binary-search", resp.topicId());
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Concurrent operations should complete within timeout");
        executor.shutdown();

        assertEquals(threadCount * operationsPerThread, successCount.get(),
                "All concurrent read operations on cached session must succeed");
    }

    @Test
    @DisplayName("RES-01: clearInMemoryCache invalidates all entries")
    void testClearInMemoryCache_InvalidatesAll() {
        SessionOrchestratorService service = new SessionOrchestratorService(
                curriculumGraph, questionBank, questionSelector, masteryEngine, tutorEngine, llmProvider
        );

        SessionResponse s1 = service.startSession("binary-search", "learner-1");
        SessionResponse s2 = service.startSession("linear-search", "learner-2");

        assertTrue(service.getCachedSessionCount() >= 2);

        service.clearInMemoryCache();
        service.cleanUpCache();

        assertEquals(0, service.getCachedSessionCount());
        assertThrows(SessionNotFoundException.class, () -> service.getSession(s1.sessionId()));
        assertThrows(SessionNotFoundException.class, () -> service.getSession(s2.sessionId()));
    }
}
