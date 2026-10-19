package com.javadsamastery.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javadsamastery.JavaDsaMasteryApplication;
import com.javadsamastery.ai.mock.MockLLMProvider;
import com.javadsamastery.ai.provider.AIProviderException;
import com.javadsamastery.ai.provider.LLMProvider;
import com.javadsamastery.domain.content.QuestionBank;
import com.javadsamastery.domain.content.TopicLoader;
import com.javadsamastery.domain.curriculum.TopicDefinition;
import com.javadsamastery.domain.evidence.AttemptContext;
import com.javadsamastery.domain.evidence.EvaluationResult;
import com.javadsamastery.domain.evidence.EvidenceRecord;
import com.javadsamastery.domain.learner.LearnerProfile;
import com.javadsamastery.domain.lesson.LearningState;
import com.javadsamastery.domain.ports.AttemptRepositoryPort;
import com.javadsamastery.domain.ports.AttemptRepositoryPort.AttemptRecord;
import com.javadsamastery.domain.ports.EvidenceRepositoryPort;
import com.javadsamastery.domain.ports.LearnerRepositoryPort;
import com.javadsamastery.domain.ports.SessionRepositoryPort;
import com.javadsamastery.domain.ports.SessionRepositoryPort.SessionSnapshot;
import com.javadsamastery.domain.ports.TopicMasteryRepositoryPort;
import com.javadsamastery.domain.ports.TopicMasteryRepositoryPort.MasterySnapshotRecord;
import com.javadsamastery.domain.topic.CurriculumGraph;
import com.javadsamastery.dto.HintResponse;
import com.javadsamastery.dto.QuestionResponse;
import com.javadsamastery.dto.SessionResponse;
import com.javadsamastery.dto.SubmitAnswerResponse;
import com.javadsamastery.engine.AITutorEngine;
import com.javadsamastery.engine.MasteryEngine;
import com.javadsamastery.engine.QuestionSelector;
import com.javadsamastery.service.SessionOrchestratorService;
import com.javadsamastery.service.SessionReaperService;
import com.javadsamastery.service.TransactionalSubmissionService;
import com.javadsamastery.service.util.SubmissionFingerprint;
import com.javadsamastery.service.exception.AIProviderUnavailableException;
import com.javadsamastery.service.exception.DuplicateAttemptException;
import com.javadsamastery.service.exception.SessionExpiredException;
import com.javadsamastery.service.exception.StaleSessionVersionException;
import com.javadsamastery.session.LessonFlow;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.Socket;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = JavaDsaMasteryApplication.class, properties = {
        "spring.profiles.active=postgres",
        "javadsa.persistence.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@ActiveProfiles("postgres")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TransactionalPostgresSessionIntegrationTest {

    private static final String JDBC_URL = System.getProperty("spring.datasource.url",
            System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://127.0.0.1:5433/java_dsa_mastery_phase2b_test"));
    private static final String USERNAME = System.getProperty("spring.datasource.username",
            System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "postgres"));
    private static final String PASSWORD = System.getProperty("spring.datasource.password",
            System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));

    private static boolean isLocalPostgresAvailable() {
        try {
            java.net.URI uri = java.net.URI.create(JDBC_URL.substring(5));
            String host = uri.getHost() != null ? uri.getHost() : "127.0.0.1";
            int port = uri.getPort() > 0 ? uri.getPort() : 5433;
            try (Socket socket = new Socket(host, port)) {
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeAll
    static void checkEnvironmentAndMigrate() {
        Assumptions.assumeTrue(isLocalPostgresAvailable(),
                "Local test PostgreSQL 18 instance on port 5433 is not running; skipping integration tests.");

        Flyway flyway = Flyway.configure()
                .dataSource(JDBC_URL, USERNAME, PASSWORD)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
    }

    @Autowired
    private SessionOrchestratorService sessionService;

    @Autowired
    private SessionRepositoryPort sessionRepository;

    @Autowired
    private AttemptRepositoryPort attemptRepository;

    @Autowired
    private EvidenceRepositoryPort evidenceRepository;

    @Autowired
    private TopicMasteryRepositoryPort topicMasteryRepository;

    @Autowired
    private LearnerRepositoryPort learnerRepository;

    @Autowired
    private TransactionalSubmissionService transactionalSubmissionService;

    @Autowired
    private LLMProvider llmProvider;

    @Autowired
    private CurriculumGraph curriculumGraph;

    @Autowired
    private QuestionBank questionBank;

    @Autowired
    private QuestionSelector questionSelector;

    @Autowired
    private MasteryEngine masteryEngine;

    @Autowired
    private AITutorEngine tutorEngine;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private SessionReaperService sessionReaperService;

    @BeforeEach
    void resetMock() {
        if (llmProvider instanceof MockLLMProvider mock) {
            mock.setCannedResponse(String.class, "{\"evaluation\": \"PASS\", \"confidenceScore\": 0, \"feedback\": \"Correct derivation of two pointer invariant verified.\"}");
        }
    }

    @Test
    @Order(1)
    @DisplayName("Scenario 1: Session creation and retrieval through PostgreSQL")
    void test01_SessionCreationAndRetrieval_Postgres() {
        String learnerId = "learner-pg-" + UUID.randomUUID().toString().substring(0, 8);
        SessionResponse response = sessionService.startSession("binary-search", learnerId);

        assertNotNull(response);
        assertNotNull(response.sessionId());
        assertEquals("binary-search", response.topicId());
        assertEquals(learnerId, response.learnerId());
        assertEquals("CONTENT_INTRO", response.currentState());

        // Verify in PostgreSQL directly
        UUID sessionUuid = UUID.fromString(response.sessionId());
        Optional<SessionSnapshot> persisted = sessionRepository.findById(sessionUuid);
        assertTrue(persisted.isPresent(), "Session must exist in PostgreSQL learning_sessions table");
        assertEquals("binary-search", persisted.get().topicId());
        assertEquals(learnerId, persisted.get().learnerId());
        assertEquals("CONTENT_INTRO", persisted.get().currentState());
        assertEquals(0L, persisted.get().version());

        assertTrue(learnerRepository.existsById(learnerId), "Learner row must be persisted");
        Optional<MasterySnapshotRecord> snapshot = topicMasteryRepository.findSnapshot(learnerId, "binary-search");
        assertTrue(snapshot.isPresent(), "Topic mastery snapshot must be initialized");
        assertEquals("NOVICE", snapshot.get().masteryLevel());
    }

    @Test
    @Order(2)
    @DisplayName("Scenario 2: Session recovery after application service restart / cache eviction")
    void test02_SessionRecovery_AfterApplicationServiceRestart() {
        String learnerId = "learner-restart-" + UUID.randomUUID().toString().substring(0, 8);
        SessionResponse created = sessionService.startSession("two-pointers", learnerId);
        String sessionId = created.sessionId();

        // Fetch first question
        QuestionResponse q1 = sessionService.getCurrentQuestion(sessionId);
        assertNotNull(q1.attemptId());

        // Submit answer
        SubmitAnswerResponse ans1 = sessionService.submitAnswer(sessionId, q1.attemptId(), "use left and right pointers", false);
        assertEquals("PASS", ans1.evaluation());

        // SIMULATE APPLICATION RESTART: Evict all in-memory cached sessions
        sessionService.clearInMemoryCache();

        // Retrieve session by ID: must rehydrate completely from PostgreSQL
        SessionResponse recovered = sessionService.getSession(sessionId);
        assertNotNull(recovered, "Session must be recovered from PostgreSQL");
        assertEquals(sessionId, recovered.sessionId());
        assertEquals(learnerId, recovered.learnerId());
        assertEquals("two-pointers", recovered.topicId());
        assertEquals(ans1.currentState(), recovered.currentState(), "State machine position must be restored exactly");
        assertEquals(ans1.currentMastery(), recovered.mastery(), "Recalculated mastery must match exactly");

        // Fetching next question after recovery must return next stage question without error
        QuestionResponse q2 = sessionService.getCurrentQuestion(sessionId);
        assertNotNull(q2.attemptId(), "Next question attempt must be issued");
        assertNotEquals(q1.attemptId(), q2.attemptId(), "Must issue fresh attempt for next stage");
    }

    @Test
    @Order(3)
    @DisplayName("Scenario 3: Current-question retrieval without unintended attempt duplication")
    void test03_CurrentQuestion_RetrievalWithoutAttemptDuplication() {
        String learnerId = "learner-nodup-" + UUID.randomUUID().toString().substring(0, 8);
        SessionResponse created = sessionService.startSession("linear-search", learnerId);
        String sessionId = created.sessionId();
        UUID sessionUuid = UUID.fromString(sessionId);

        // Call 1
        QuestionResponse q1 = sessionService.getCurrentQuestion(sessionId);
        assertNotNull(q1.attemptId());

        // Call 2
        QuestionResponse q2 = sessionService.getCurrentQuestion(sessionId);
        assertEquals(q1.attemptId(), q2.attemptId(), "Subsequent call must return same active attemptId");

        // Verify in PostgreSQL: exactly 1 attempt exists for this session with status ISSUED
        Optional<AttemptRecord> active = attemptRepository.findActiveAttemptBySessionId(sessionUuid);
        assertTrue(active.isPresent());
        assertEquals(q1.attemptId(), active.get().context().attemptId());
        assertEquals("ISSUED", active.get().status());
    }

    @Test
    @Order(4)
    @DisplayName("Scenario 4: Successful answer submission with atomic attempt consumption, evidence, and mastery updates")
    void test04_SuccessfulAnswerSubmission_AtomicUpdates() {
        String learnerId = "learner-atomic-" + UUID.randomUUID().toString().substring(0, 8);
        SessionResponse created = sessionService.startSession("sliding-window", learnerId);
        String sessionId = created.sessionId();
        UUID sessionUuid = UUID.fromString(sessionId);

        QuestionResponse q = sessionService.getCurrentQuestion(sessionId);
        UUID attemptUuid = UUID.fromString(q.attemptId());

        SubmitAnswerResponse res = sessionService.submitAnswer(sessionId, q.attemptId(), "sliding window maintains invariant", false);
        assertEquals("PASS", res.evaluation());

        // 1. Verify Attempt is CONSUMED in PostgreSQL
        AttemptRecord attempt = attemptRepository.findById(attemptUuid).orElseThrow();
        assertEquals("CONSUMED", attempt.status());
        assertNotNull(attempt.consumedAt());

        // 2. Verify Evidence Record inserted in PostgreSQL
        var evidenceOpt = evidenceRepository.findByAttemptId(attemptUuid);
        assertTrue(evidenceOpt.isPresent());
        assertEquals(EvaluationResult.PASS, evidenceOpt.get().evidenceRecord().result());
        assertEquals(attemptUuid, evidenceOpt.get().attemptId());
        assertEquals(sessionUuid, evidenceOpt.get().sessionId());

        // 3. Verify Session version updated in PostgreSQL
        SessionSnapshot sessionSnap = sessionRepository.findById(sessionUuid).orElseThrow();
        assertEquals(2L, sessionSnap.version(), "Session version must be 2 after intro advance and answer submission");

        // 4. Verify Topic Mastery Snapshot updated in PostgreSQL
        MasterySnapshotRecord mastery = topicMasteryRepository.findSnapshot(learnerId, "sliding-window").orElseThrow();
        assertTrue(mastery.version() >= 1L, "Mastery snapshot version must be incremented");
    }

    @Test
    @Order(5)
    @DisplayName("Scenario 5: Rollback when evidence insertion fails preserves ISSUED attempt status")
    void test05_Rollback_WhenEvidenceInsertionFails() {
        String learnerId = "learner-rollback-" + UUID.randomUUID().toString().substring(0, 8);
        SessionResponse created = sessionService.startSession("prefix-sum", learnerId);
        UUID sessionUuid = UUID.fromString(created.sessionId());

        QuestionResponse q = sessionService.getCurrentQuestion(created.sessionId());
        UUID attemptUuid = UUID.fromString(q.attemptId());

        // Craft a candidate evidence with mismatched topic to trigger PostgreSQL composite FK constraint violation
        EvidenceRecord mismatchedEvidence = new EvidenceRecord(
                learnerId,
                "wrong-mismatched-topic", // triggers fk_evidence_attempt_context violation
                "ps-q1",
                com.javadsamastery.domain.problem.ProblemDifficulty.EASY,
                com.javadsamastery.domain.lesson.QuestionType.CONCEPT,
                0,
                false,
                EvaluationResult.PASS,
                null,
                Instant.now()
        );

        LessonFlow detachedFlow = LessonFlow.rehydrate(
                curriculumGraph.getDefinition("prefix-sum"),
                TopicLoader.load("prefix-sum"),
                new LearnerProfile(learnerId),
                masteryEngine,
                LearningState.CONCEPT_ASSESSMENT,
                0, 0, 0, Collections.emptyList()
        );

        // Transaction must fail and roll back cleanly
        assertThrows(Exception.class, () -> {
            transactionalSubmissionService.commitSubmission(
                    sessionUuid,
                    attemptUuid,
                    learnerId,
                    "prefix-sum",
                    mismatchedEvidence,
                    "feedback",
                    "dummy-fingerprint",
                    0L,
                    detachedFlow
            );
        });

        // VERIFY ROLLBACK: Attempt in PostgreSQL must STILL be ISSUED!
        AttemptRecord attemptAfterRollback = attemptRepository.findById(attemptUuid).orElseThrow();
        assertEquals("ISSUED", attemptAfterRollback.status(), "Attempt status must remain ISSUED after rollback");
        assertNull(attemptAfterRollback.consumedAt());

        // VERIFY: 0 evidence records exist for this attempt
        assertTrue(evidenceRepository.findByAttemptId(attemptUuid).isEmpty(), "Zero evidence records must exist");
    }

    @Test
    @Order(6)
    @DisplayName("Scenario 6: Rollback when session version is stale throws StaleSessionVersionException")
    void test06_Rollback_WhenSessionVersionStale() {
        String learnerId = "learner-stale-" + UUID.randomUUID().toString().substring(0, 8);
        SessionResponse created = sessionService.startSession("binary-search", learnerId);
        UUID sessionUuid = UUID.fromString(created.sessionId());

        QuestionResponse q = sessionService.getCurrentQuestion(created.sessionId());
        UUID attemptUuid = UUID.fromString(q.attemptId());

        EvidenceRecord validEvidence = new EvidenceRecord(
                learnerId,
                "binary-search",
                "bs-q1",
                com.javadsamastery.domain.problem.ProblemDifficulty.EASY,
                com.javadsamastery.domain.lesson.QuestionType.CONCEPT,
                0,
                false,
                EvaluationResult.PASS,
                null,
                Instant.now()
        );

        LessonFlow detachedFlow = LessonFlow.rehydrate(
                curriculumGraph.getDefinition("binary-search"),
                TopicLoader.load("binary-search"),
                new LearnerProfile(learnerId),
                masteryEngine,
                LearningState.CONCEPT_ASSESSMENT,
                0, 0, 0, Collections.emptyList()
        );

        // Commit with bogus expected version (999 instead of 0)
        assertThrows(StaleSessionVersionException.class, () -> {
            transactionalSubmissionService.commitSubmission(
                    sessionUuid,
                    attemptUuid,
                    learnerId,
                    "binary-search",
                    validEvidence,
                    "feedback",
                    "fingerprint",
                    999L, // STALE VERSION
                    detachedFlow
            );
        });

        // Attempt remains ISSUED
        assertEquals("ISSUED", attemptRepository.findById(attemptUuid).orElseThrow().status());
    }

    @Test
    @Order(7)
    @DisplayName("Scenario 7: Concurrent submissions for one attempt: at most one new transition commits")
    void test07_ConcurrentSubmissions_SingleAttemptOnlyOneCommits() throws Exception {
        String learnerId = "learner-concurrent-" + UUID.randomUUID().toString().substring(0, 8);
        SessionResponse created = sessionService.startSession("two-pointers", learnerId);
        String sessionId = created.sessionId();

        QuestionResponse q = sessionService.getCurrentQuestion(sessionId);
        String attemptId = q.attemptId();

        int threadCount = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    // Each thread sends a slightly different answer to test duplicate detection
                    String ans = "answer from thread " + idx;
                    SubmitAnswerResponse res = sessionService.submitAnswer(sessionId, attemptId, ans, false);
                    if ("PASS".equals(res.evaluation())) {
                        successCount.incrementAndGet();
                    }
                } catch (DuplicateAttemptException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        finishLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly one unique transition commits; all conflicting threads get DuplicateAttemptException
        assertEquals(1, successCount.get(), "Exactly one concurrent submission must commit");
        assertEquals(threadCount - 1, conflictCount.get(), "All other concurrent submissions must be rejected with DuplicateAttemptException");

        // Verify in PostgreSQL: exactly 1 evidence record exists for this attempt
        UUID attemptUuid = UUID.fromString(attemptId);
        assertTrue(evidenceRepository.findByAttemptId(attemptUuid).isPresent());
    }

    @Test
    @Order(8)
    @DisplayName("Scenario 8: Safe precise idempotent retry after successful commit returns original result; different answer rejected")
    void test08_PreciseIdempotentRetry_AfterSuccessfulCommit() {
        String learnerId = "learner-idemp-" + UUID.randomUUID().toString().substring(0, 8);
        SessionResponse created = sessionService.startSession("binary-search", learnerId);
        String sessionId = created.sessionId();

        QuestionResponse q = sessionService.getCurrentQuestion(sessionId);
        String attemptId = q.attemptId();
        String originalAnswer = "divide and conquer search interval";

        // 1. Initial submission succeeds
        SubmitAnswerResponse res1 = sessionService.submitAnswer(sessionId, attemptId, originalAnswer, false);
        assertEquals("PASS", res1.evaluation());

        // Verify in DB that fingerprint is stored in dedicated column and feedback is clean
        EvidenceRepositoryPort.PersistedEvidence persisted = evidenceRepository.findByAttemptId(UUID.fromString(attemptId)).orElseThrow();
        assertEquals(SubmissionFingerprint.computeFingerprint(attemptId, originalAnswer), persisted.idempotencyFingerprint(),
                "Dedicated idempotency_fingerprint column must hold computed SHA-256 fingerprint");
        assertFalse(persisted.feedback().contains("<!-- idemp:"), "Feedback in DB must not contain internal marker");
        assertFalse(res1.feedback().contains("<!-- idemp:"), "API response feedback must not contain internal marker");

        // 2. Exact retry with SAME answer returns original result (HTTP 200 equivalent)
        SubmitAnswerResponse res2 = sessionService.submitAnswer(sessionId, attemptId, originalAnswer, false);
        assertEquals("PASS", res2.evaluation());
        assertEquals(res1.attemptId(), res2.attemptId());
        assertEquals(res1.currentState(), res2.currentState());
        assertFalse(res2.feedback().contains("<!-- idemp:"), "API response feedback on retry must not contain internal marker");

        // 3. Retry with DIFFERENT answer is strictly rejected with DuplicateAttemptException (HTTP 409 Conflict)
        String differentAnswer = "a completely different answer payload";
        assertThrows(DuplicateAttemptException.class, () -> {
            sessionService.submitAnswer(sessionId, attemptId, differentAnswer, false);
        }, "Submitting attempt with different answer must be rejected");

        // Verify that database was NOT modified by the rejected retry
        EvidenceRepositoryPort.PersistedEvidence afterRejected = evidenceRepository.findByAttemptId(UUID.fromString(attemptId)).orElseThrow();
        assertEquals(persisted.idempotencyFingerprint(), afterRejected.idempotencyFingerprint(), "Database record must not be modified by conflicting retry");
    }

    @Test
    @Order(9)
    @DisplayName("Scenario 9: Two sessions updating the same learner-topic mastery: row lock prevents lost updates")
    void test09_CrossSessionMasteryUpdates_RowLockPreventsLostUpdates() {
        String learnerId = "learner-cross-session-" + UUID.randomUUID().toString().substring(0, 8);
        String topicId = "sliding-window";

        // Learner opens Session 1 and Session 2 concurrently on topic sliding-window
        SessionResponse s1 = sessionService.startSession(topicId, learnerId);
        SessionResponse s2 = sessionService.startSession(topicId, learnerId);

        QuestionResponse q1 = sessionService.getCurrentQuestion(s1.sessionId());
        QuestionResponse q2 = sessionService.getCurrentQuestion(s2.sessionId());

        // Submit in Session 1
        SubmitAnswerResponse r1 = sessionService.submitAnswer(s1.sessionId(), q1.attemptId(), "window expansion and contraction", false);
        assertEquals("PASS", r1.evaluation());

        // Submit in Session 2
        SubmitAnswerResponse r2 = sessionService.submitAnswer(s2.sessionId(), q2.attemptId(), "window expansion and contraction", false);
        assertEquals("PASS", r2.evaluation());

        // Verify topic mastery snapshot in PostgreSQL reflects combined progress without lost updates
        MasterySnapshotRecord snapshot = topicMasteryRepository.findSnapshot(learnerId, topicId).orElseThrow();
        assertEquals(2, snapshot.independentConceptPasses(), "Both independent passes from Session 1 and 2 must be recorded");
        assertTrue(snapshot.version() >= 2L, "Mastery snapshot version must be incremented by each session update");
    }

    @Test
    @Order(10)
    @DisplayName("Scenario 10: Concurrent creation of a previously nonexistent mastery snapshot is race-safe")
    void test10_ConcurrentCreationOfNonexistentMasterySnapshot() throws Exception {
        String learnerId = "learner-race-snap-" + UUID.randomUUID().toString().substring(0, 8);
        learnerRepository.save(learnerId, true);

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    MasterySnapshotRecord locked = topicMasteryRepository.lockOrInitialize(learnerId, "hashing");
                    if (locked != null && "NOVICE".equals(locked.masteryLevel())) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        finishLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threadCount, successCount.get(), "All concurrent callers must safely lockOrInitialize without duplicate key errors");
        assertTrue(topicMasteryRepository.findSnapshot(learnerId, "hashing").isPresent());
    }

    @Test
    @Order(11)
    @DisplayName("Scenario 11: Recovery of mastery from authoritative evidence after snapshot deletion")
    void test11_RecoveryOfMasteryFromAuthoritativeEvidence() {
        String learnerId = "learner-rebuild-" + UUID.randomUUID().toString().substring(0, 8);
        SessionResponse s = sessionService.startSession("prefix-sum", learnerId);

        QuestionResponse q1 = sessionService.getCurrentQuestion(s.sessionId());
        sessionService.submitAnswer(s.sessionId(), q1.attemptId(), "prefix array cumulative sum", false);

        // Delete the topic mastery snapshot directly from PostgreSQL
        topicMasteryRepository.deleteByLearnerIdAndTopicId(learnerId, "prefix-sum");
        assertTrue(topicMasteryRepository.findSnapshot(learnerId, "prefix-sum").isEmpty());

        // Reconstruct mastery from authoritative evidence ledger
        List<EvidenceRecord> history = evidenceRepository.findChronologicalHistory(learnerId, "prefix-sum");
        assertFalse(history.isEmpty(), "Evidence records must remain in PostgreSQL");

        LearnerProfile reconstructedProfile = new LearnerProfile(learnerId);
        reconstructedProfile.getTopicState("prefix-sum").evaluateFromHistory(history);

        assertEquals("prefix-sum", reconstructedProfile.getTopicState("prefix-sum").getTopicId());
        assertEquals(1, reconstructedProfile.getTopicState("prefix-sum").getIndependentConceptPasses());
    }

    @Test
    @Order(12)
    @DisplayName("Scenario 12: AI provider failure does not penalize learner or burn attempt")
    void test12_ProviderFailure_DoesNotPenalizeLearnerOrBurnAttempt() {
        String learnerId = "learner-aifail-" + UUID.randomUUID().toString().substring(0, 8);
        SessionResponse s = sessionService.startSession("binary-search", learnerId);

        QuestionResponse q = sessionService.getCurrentQuestion(s.sessionId());
        UUID attemptUuid = UUID.fromString(q.attemptId());

        if (llmProvider instanceof MockLLMProvider mock) {
            mock.setSimulateFailure(true);
        }

        try {
            // Must throw AIProviderUnavailableException
            assertThrows(AIProviderUnavailableException.class, () -> {
                sessionService.submitAnswer(s.sessionId(), q.attemptId(), "my answer", false);
            });
        } finally {
            if (llmProvider instanceof MockLLMProvider mock) {
                mock.setSimulateFailure(false);
            }
        }

        // Verify in PostgreSQL: Attempt is STILL ISSUED!
        AttemptRecord attempt = attemptRepository.findById(attemptUuid).orElseThrow();
        assertEquals("ISSUED", attempt.status(), "Attempt must remain in ISSUED status after AI provider failure");
        assertNull(attempt.consumedAt());

        // 0 evidence records exist
        assertTrue(evidenceRepository.findByAttemptId(attemptUuid).isEmpty(), "Zero evidence records must be recorded");
    }

    @Test
    @Order(13)
    @DisplayName("Scenario 13: In-memory mode operates independently when persistence is disabled")
    void test13_InMemoryModePreservation() {
        // Construct standalone in-memory SessionOrchestratorService with empty persistence ports
        SessionOrchestratorService inMemoryService = new SessionOrchestratorService(
                curriculumGraph,
                questionBank,
                questionSelector,
                masteryEngine,
                tutorEngine,
                llmProvider,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );

        assertFalse(inMemoryService.isPersistenceEnabled());

        SessionResponse session = inMemoryService.startSession("linear-search", "student-mem");
        assertNotNull(session.sessionId());
        assertEquals("CONTENT_INTRO", session.currentState());

        QuestionResponse q = inMemoryService.getCurrentQuestion(session.sessionId());
        assertNotNull(q.attemptId());

        SubmitAnswerResponse res = inMemoryService.submitAnswer(session.sessionId(), q.attemptId(), "sequential scan", false);
        assertEquals("PASS", res.evaluation());
    }

    @Test
    @Order(14)
    @DisplayName("Scenario 14: Active attempt recovery restores ISSUED attempt across cache eviction without duplicate")
    void test14_ActiveAttemptRecovery_AcrossCacheEviction() {
        String learnerId = "learner-recovery-" + UUID.randomUUID().toString().substring(0, 8);
        String topicId = "binary-search";

        // 1. Create a learning session
        SessionResponse session = sessionService.startSession(topicId, learnerId);
        String sessionId = session.sessionId();
        UUID sessionUuid = UUID.fromString(sessionId);

        // 2. Obtain an active, unconsumed ISSUED attempt
        QuestionResponse q1 = sessionService.getCurrentQuestion(sessionId);
        String originalAttemptId = q1.attemptId();
        assertNotNull(originalAttemptId, "Original attempt ID must not be null");
        UUID attemptUuid = UUID.fromString(originalAttemptId);

        // Verify active ISSUED attempt in PostgreSQL
        AttemptRecord activeInDb = attemptRepository.findActiveAttemptBySessionId(sessionUuid).orElseThrow();
        assertEquals(originalAttemptId, activeInDb.context().attemptId());
        assertEquals("ISSUED", activeInDb.status());
        assertNull(activeInDb.consumedAt());

        // 3. Clear session's in-memory cache without deleting database records
        sessionService.clearInMemoryCache();

        // 4. Retrieve session and current question again
        SessionResponse restoredSession = sessionService.getSession(sessionId);
        assertNotNull(restoredSession);
        assertEquals(sessionId, restoredSession.sessionId());
        assertEquals(learnerId, restoredSession.learnerId());

        QuestionResponse q2 = sessionService.getCurrentQuestion(sessionId);

        // 5. Verify original active attempt is restored correctly
        assertEquals(originalAttemptId, q2.attemptId(), "Restored question must reference identical original attempt ID");
        assertEquals(q1.content(), q2.content(), "Restored question content must match original prompt");

        // 6. Verify retrieval does not create an unintended duplicate attempt
        Integer attemptCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM attempts WHERE session_id = ?",
                Integer.class,
                sessionUuid
        );
        assertEquals(1, attemptCount, "Exactly 1 attempt record must exist in PostgreSQL; no duplicates created");

        // 7. Submit valid answer and confirm restored attempt can be consumed exactly once
        SubmitAnswerResponse submitRes = sessionService.submitAnswer(sessionId, originalAttemptId, "binary search interval division", false);
        assertEquals("PASS", submitRes.evaluation());

        // Verify in DB that attempt is now CONSUMED and exactly 1 evidence record exists
        AttemptRecord consumedInDb = attemptRepository.findById(attemptUuid).orElseThrow();
        assertEquals("CONSUMED", consumedInDb.status());
        assertNotNull(consumedInDb.consumedAt());

        assertTrue(evidenceRepository.findByAttemptId(attemptUuid).isPresent(), "Evidence record must be saved for consumed attempt");

        // Subsequent submission of same consumed attempt with different answer throws DuplicateAttemptException
        assertThrows(DuplicateAttemptException.class, () -> {
            sessionService.submitAnswer(sessionId, originalAttemptId, "different answer after consumed", false);
        });
    }

    @Test
    @Order(15)
    @DisplayName("Scenario 15: Hint requests and count persist across in-memory cache eviction")
    void test15_HintPersistence_AcrossCacheEviction() {
        String learnerId = "learner-hints-" + UUID.randomUUID().toString().substring(0, 8);
        String topicId = "sliding-window";

        // 1. Create a session and active attempt
        SessionResponse session = sessionService.startSession(topicId, learnerId);
        String sessionId = session.sessionId();
        UUID sessionUuid = UUID.fromString(sessionId);

        QuestionResponse q1 = sessionService.getCurrentQuestion(sessionId);
        String attemptId = q1.attemptId();
        UUID attemptUuid = UUID.fromString(attemptId);
        assertEquals(0, q1.hintsUsed());

        // 2. Request a hint using existing application/API path
        HintResponse hint1 = sessionService.requestHint(sessionId, attemptId);
        assertEquals(1, hint1.hintLevel());
        assertNotNull(hint1.hintText());

        // Verify in PostgreSQL: hints_requested == 1 and status == ISSUED
        AttemptRecord dbAttempt1 = attemptRepository.findById(attemptUuid).orElseThrow();
        assertEquals(1, dbAttempt1.hintsRequested(), "DB hints_requested must be 1");
        assertEquals("ISSUED", dbAttempt1.status());

        // 3. Clear in-memory cache without touching database records
        sessionService.clearInMemoryCache();

        // 4. Reload session and verify question reflects persisted hint count
        QuestionResponse qRestored = sessionService.getCurrentQuestion(sessionId);
        assertEquals(attemptId, qRestored.attemptId());
        assertEquals(1, qRestored.hintsUsed(), "Restored question must reflect persisted hintsUsed = 1");

        // Request a second hint after cache wipe
        HintResponse hint2 = sessionService.requestHint(sessionId, attemptId);
        assertEquals(2, hint2.hintLevel());

        // 5. Verify hint counts and DB records match in-memory state
        AttemptRecord dbAttempt2 = attemptRepository.findById(attemptUuid).orElseThrow();
        assertEquals(2, dbAttempt2.hintsRequested(), "DB hints_requested must be updated to 2");

        // Submit answer and confirm evidence records hint count of 2
        SubmitAnswerResponse submitRes = sessionService.submitAnswer(sessionId, attemptId, "window expansion and contraction", false);
        assertEquals("PASS", submitRes.evaluation());
        assertEquals(2, submitRes.hintsUsed(), "Submit response must report 2 hints used");

        EvidenceRepositoryPort.PersistedEvidence evidence = evidenceRepository.findByAttemptId(attemptUuid).orElseThrow();
        assertEquals(2, evidence.evidenceRecord().hintCount(), "Persisted evidence must record 2 hints used");
    }

    @Test
    @Order(16)
    @DisplayName("Task 2D.2-A: Bounded cache eviction transparently rehydrates session from PostgreSQL without data loss")
    void test16_BoundedCacheEviction_TransparentlyRehydratesWithoutDataLoss() {
        String learnerId = "learner-rehydrate-" + UUID.randomUUID().toString().substring(0, 8);
        SessionResponse session = sessionService.startSession("binary-search", learnerId);
        String sessionId = session.sessionId();
        UUID sessionUuid = UUID.fromString(sessionId);

        QuestionResponse qResp = sessionService.getCurrentQuestion(sessionId);
        String attemptId = qResp.attemptId();
        UUID attemptUuid = UUID.fromString(attemptId);

        // Verify attempt exists in PostgreSQL
        assertTrue(attemptRepository.findById(attemptUuid).isPresent());

        // Simulate cache eviction by clearing in-memory cache
        sessionService.clearInMemoryCache();
        assertEquals(0, sessionService.getCachedSessionCount());

        // Submit answer on the evicted session: must rehydrate and commit without error
        SubmitAnswerResponse submitResp = sessionService.submitAnswer(sessionId, attemptId, "binary search divides interval in half", false);
        assertEquals("PASS", submitResp.evaluation());

        // Verify attempt transitioned to CONSUMED in PostgreSQL
        AttemptRecord consumed = attemptRepository.findById(attemptUuid).orElseThrow();
        assertEquals("CONSUMED", consumed.status());

        // Verify session version incremented and updated in PostgreSQL
        SessionSnapshot dbSession = sessionRepository.findById(sessionUuid).orElseThrow();
        assertTrue(dbSession.version() > 0);
    }

    @Test
    @Order(17)
    @DisplayName("Task 2D.2-B: Real PostgreSQL session reaper expires abandoned attempts and marks expired sessions")
    void test17_PostgresSessionReaper_ExpiresSessionsAndIssuedAttempts() {
        String learnerId = "learner-reaper-" + UUID.randomUUID().toString().substring(0, 8);
        SessionResponse session = sessionService.startSession("sliding-window", learnerId);
        String sessionId = session.sessionId();
        UUID sessionUuid = UUID.fromString(sessionId);

        QuestionResponse qResp = sessionService.getCurrentQuestion(sessionId);
        String attemptId = qResp.attemptId();
        UUID attemptUuid = UUID.fromString(attemptId);

        // Verify attempt is initially ISSUED
        AttemptRecord initialAttempt = attemptRepository.findById(attemptUuid).orElseThrow();
        assertEquals("ISSUED", initialAttempt.status());

        // Directly expire session in PostgreSQL by setting expires_at 1 hour in the past
        jdbcTemplate.update("UPDATE learning_sessions SET expires_at = NOW() - INTERVAL '1 hour' WHERE session_id = ?", sessionUuid);

        // Run the session reaper
        SessionReaperService.ReaperResult result = sessionReaperService.reapExpiredSessions(Instant.now());
        assertTrue(result.expiredSessionsCount() >= 1, "Reaper must have marked at least 1 session expired");
        assertTrue(result.expiredAttemptsCount() >= 1, "Reaper must have expired at least 1 ISSUED attempt");

        // Verify attempt in PostgreSQL is now EXPIRED and consumed_at is recorded
        AttemptRecord reapedAttempt = attemptRepository.findById(attemptUuid).orElseThrow();
        assertEquals("EXPIRED", reapedAttempt.status(), "Unconsumed attempt must be transitioned to EXPIRED");
        assertNotNull(reapedAttempt.consumedAt(), "consumed_at must be populated");

        // Verify session in PostgreSQL is marked is_completed = true
        SessionSnapshot reapedSession = sessionRepository.findById(sessionUuid).orElseThrow();
        assertTrue(reapedSession.isCompleted(), "Expired session must be marked completed");

        // Idempotent rerun: executing reaper again must update 0 rows for this session/attempt
        SessionReaperService.ReaperResult rerunResult = sessionReaperService.reapExpiredSessions(Instant.now());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM attempts WHERE session_id = ? AND status = 'ISSUED'", Integer.class, sessionUuid));
    }

    @Test
    @Order(18)
    @DisplayName("Task 2D.2-C: Expired session in PostgreSQL rejects submission and rolls back without altering DB")
    void test18_ExpiredSessionInPostgres_RejectsSubmissionAndRollsBack() {
        String learnerId = "learner-expired-pg-" + UUID.randomUUID().toString().substring(0, 8);
        SessionResponse session = sessionService.startSession("two-pointers", learnerId);
        String sessionId = session.sessionId();
        UUID sessionUuid = UUID.fromString(sessionId);

        QuestionResponse qResp = sessionService.getCurrentQuestion(sessionId);
        String attemptId = qResp.attemptId();
        UUID attemptUuid = UUID.fromString(attemptId);

        // Expire session in PostgreSQL and clear cache
        jdbcTemplate.update("UPDATE learning_sessions SET expires_at = NOW() - INTERVAL '10 minutes' WHERE session_id = ?", sessionUuid);
        sessionService.clearInMemoryCache();

        // Submitting an answer on the expired session must throw SessionExpiredException
        assertThrows(SessionExpiredException.class, () ->
                sessionService.submitAnswer(sessionId, attemptId, "window left and right pointers", false));

        // Verify database: attempt remains in original state and no evidence record was inserted
        AttemptRecord dbAttempt = attemptRepository.findById(attemptUuid).orElseThrow();
        assertEquals("ISSUED", dbAttempt.status(), "Rollback must ensure attempt was not consumed");
        assertTrue(evidenceRepository.findByAttemptId(attemptUuid).isEmpty(), "Zero evidence records must be inserted");
    }
}
