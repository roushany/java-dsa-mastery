package com.javadsamastery.service;

import com.javadsamastery.ai.dto.AIAssessment;
import com.javadsamastery.ai.dto.GeneratedQuestion;
import com.javadsamastery.ai.dto.HintDto;
import com.javadsamastery.ai.provider.LLMProvider;
import com.javadsamastery.ai.service.impl.LLMHintGenerator;
import com.javadsamastery.domain.content.QuestionBank;
import com.javadsamastery.domain.content.QuestionDefinition;
import com.javadsamastery.domain.content.TopicLoader;
import com.javadsamastery.domain.content.TopicTemplate;
import com.javadsamastery.domain.curriculum.TopicDefinition;
import com.javadsamastery.domain.evidence.AttemptContext;
import com.javadsamastery.domain.evidence.EvaluationResult;
import com.javadsamastery.domain.evidence.EvidencePolicy;
import com.javadsamastery.domain.evidence.EvidenceRecord;
import com.javadsamastery.domain.learner.LearnerProfile;
import com.javadsamastery.domain.lesson.LearningState;
import com.javadsamastery.domain.ports.AttemptRepositoryPort;
import com.javadsamastery.domain.ports.AttemptRepositoryPort.AttemptRecord;
import com.javadsamastery.domain.ports.EvidenceRepositoryPort;
import com.javadsamastery.domain.ports.EvidenceRepositoryPort.PersistedEvidence;
import com.javadsamastery.domain.ports.LearnerRepositoryPort;
import com.javadsamastery.domain.ports.SessionRepositoryPort;
import com.javadsamastery.domain.ports.SessionRepositoryPort.SessionSnapshot;
import com.javadsamastery.domain.ports.TopicMasteryRepositoryPort;
import com.javadsamastery.domain.topic.CurriculumGraph;
import com.javadsamastery.dto.*;
import com.javadsamastery.engine.AITutorEngine;
import com.javadsamastery.engine.MasteryEngine;
import com.javadsamastery.engine.QuestionSelector;
import com.javadsamastery.service.exception.*;
import com.javadsamastery.service.util.SubmissionFingerprint;
import com.javadsamastery.session.LearningSession;
import com.javadsamastery.session.LessonFlow;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class SessionOrchestratorService {

    private final CurriculumGraph curriculumGraph;
    private final QuestionBank questionBank;
    private final QuestionSelector questionSelector;
    private final MasteryEngine masteryEngine;
    private final AITutorEngine tutorEngine;
    private final LLMHintGenerator hintGenerator;

    private final SessionRepositoryPort sessionRepository;
    private final AttemptRepositoryPort attemptRepository;
    private final EvidenceRepositoryPort evidenceRepository;
    private final TopicMasteryRepositoryPort topicMasteryRepository;
    private final LearnerRepositoryPort learnerRepository;
    private final TransactionalSubmissionService transactionalSubmissionService;

    private final Cache<String, SessionContext> sessionCache;
    private final Set<String> knownLearners = ConcurrentHashMap.newKeySet();
    private final long sessionTtlHours;

    @Autowired
    public SessionOrchestratorService(
            CurriculumGraph curriculumGraph,
            QuestionBank questionBank,
            QuestionSelector questionSelector,
            MasteryEngine masteryEngine,
            AITutorEngine tutorEngine,
            LLMProvider llmProvider,
            Optional<SessionRepositoryPort> sessionRepository,
            Optional<AttemptRepositoryPort> attemptRepository,
            Optional<EvidenceRepositoryPort> evidenceRepository,
            Optional<TopicMasteryRepositoryPort> topicMasteryRepository,
            Optional<LearnerRepositoryPort> learnerRepository,
            Optional<TransactionalSubmissionService> transactionalSubmissionService,
            @Value("${javadsa.cache.session.max-entries:1000}") long maxCacheEntries,
            @Value("${javadsa.cache.session.idle-timeout-minutes:30}") long idleTimeoutMinutes,
            @Value("${javadsa.session.ttl-hours:24}") long sessionTtlHours) {
        this.curriculumGraph = curriculumGraph;
        this.questionBank = questionBank;
        this.questionSelector = questionSelector;
        this.masteryEngine = masteryEngine;
        this.tutorEngine = tutorEngine;
        this.hintGenerator = new LLMHintGenerator(llmProvider);
        this.sessionRepository = sessionRepository.orElse(null);
        this.attemptRepository = attemptRepository.orElse(null);
        this.evidenceRepository = evidenceRepository.orElse(null);
        this.topicMasteryRepository = topicMasteryRepository.orElse(null);
        this.learnerRepository = learnerRepository.orElse(null);
        this.transactionalSubmissionService = transactionalSubmissionService.orElse(null);
        this.sessionTtlHours = sessionTtlHours;
        this.sessionCache = Caffeine.newBuilder()
                .maximumSize(maxCacheEntries)
                .expireAfterAccess(idleTimeoutMinutes, TimeUnit.MINUTES)
                .build();
    }

    public SessionOrchestratorService(
            CurriculumGraph curriculumGraph,
            QuestionBank questionBank,
            QuestionSelector questionSelector,
            MasteryEngine masteryEngine,
            AITutorEngine tutorEngine,
            LLMProvider llmProvider,
            Optional<SessionRepositoryPort> sessionRepository,
            Optional<AttemptRepositoryPort> attemptRepository,
            Optional<EvidenceRepositoryPort> evidenceRepository,
            Optional<TopicMasteryRepositoryPort> topicMasteryRepository,
            Optional<LearnerRepositoryPort> learnerRepository,
            Optional<TransactionalSubmissionService> transactionalSubmissionService) {
        this(curriculumGraph, questionBank, questionSelector, masteryEngine, tutorEngine, llmProvider,
                sessionRepository, attemptRepository, evidenceRepository, topicMasteryRepository,
                learnerRepository, transactionalSubmissionService, 1000L, 30L, 24L);
    }

    public SessionOrchestratorService(
            CurriculumGraph curriculumGraph,
            QuestionBank questionBank,
            QuestionSelector questionSelector,
            MasteryEngine masteryEngine,
            AITutorEngine tutorEngine,
            LLMProvider llmProvider) {
        this(curriculumGraph, questionBank, questionSelector, masteryEngine, tutorEngine, llmProvider,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public boolean isPersistenceEnabled() {
        return sessionRepository != null && attemptRepository != null && evidenceRepository != null && transactionalSubmissionService != null;
    }

    public SessionResponse startSession(String topicId, String optionalLearnerId) {
        TopicDefinition topicDef = curriculumGraph.getDefinition(topicId);
        if (topicDef == null) {
            throw new InvalidTopicException("Unknown topic: " + topicId);
        }

        TopicTemplate topicTemplate;
        try {
            topicTemplate = TopicLoader.load(topicId);
        } catch (Exception e) {
            throw new InvalidTopicException("Failed to load topic template for: " + topicId);
        }

        String learnerId = (optionalLearnerId != null && !optionalLearnerId.isBlank())
                ? optionalLearnerId
                : "learner-" + UUID.randomUUID().toString().substring(0, 8);

        String sessionId = UUID.randomUUID().toString();
        LearnerProfile profile = new LearnerProfile(learnerId);

        LearningSession learningSession = new LearningSession(
                profile, masteryEngine, questionSelector, tutorEngine);
        learningSession.start();

        LessonFlow lessonFlow = new LessonFlow(
                topicDef, topicTemplate, profile, masteryEngine);

        Instant now = Instant.now();
        Instant expiresAt = now.plus(sessionTtlHours, ChronoUnit.HOURS);

        SessionContext context = new SessionContext(
                sessionId, learnerId, topicId, topicDef, topicTemplate,
                profile, learningSession, lessonFlow, now, expiresAt);

        if (isPersistenceEnabled()) {
            if (learnerRepository != null) {
                learnerRepository.save(learnerId, true);
            }
            SessionSnapshot sessionSnapshot = new SessionSnapshot(
                    UUID.fromString(sessionId),
                    learnerId,
                    topicId,
                    lessonFlow.getCurrentState().name(),
                    lessonFlow.getNominalStageIndex(),
                    lessonFlow.getPedagogicalFailures(),
                    lessonFlow.getTotalAttemptsInState(),
                    false,
                    0L,
                    now,
                    now,
                    expiresAt
            );
            sessionRepository.save(sessionSnapshot);

            if (topicMasteryRepository != null) {
                topicMasteryRepository.lockOrInitialize(learnerId, topicId);
            }
        }

        sessionCache.put(sessionId, context);
        knownLearners.add(learnerId);
        return toSessionResponse(context);
    }

    public boolean isLearnerExisting(String learnerId) {
        if (learnerId == null || learnerId.isBlank()) {
            return false;
        }
        if (learnerRepository != null && learnerRepository.existsById(learnerId)) {
            return true;
        }
        if (knownLearners.contains(learnerId)) {
            return true;
        }
        return sessionCache.asMap().values().stream().anyMatch(s -> learnerId.equals(s.learnerId));
    }

    public SessionResponse getSession(String sessionId) {
        SessionContext context = getSessionContext(sessionId);
        context.lock.lock();
        try {
            return toSessionResponse(context);
        } finally {
            context.lock.unlock();
        }
    }

    public QuestionResponse getCurrentQuestion(String sessionId) {
        SessionContext context = getSessionContext(sessionId);
        context.lock.lock();
        try {
            LessonFlow flow = context.lessonFlow;
            LearningSession session = context.learningSession;

            if (flow.getCurrentState() == LearningState.COMPLETED || flow.getCurrentState() == LearningState.FAILED_TERMINATED) {
                return new QuestionResponse(
                        null,
                        sessionId,
                        context.topicId,
                        flow.getCurrentState().name(),
                        null,
                        "Lesson terminated: " + flow.getCurrentState().name(),
                        0,
                        true
                );
            }

            if (isPersistenceEnabled()) {
                UUID sessionUuid = UUID.fromString(sessionId);
                Optional<AttemptRecord> activeAttemptOpt = attemptRepository.findActiveAttemptBySessionId(sessionUuid);
                if (activeAttemptOpt.isPresent()) {
                    AttemptRecord activeRecord = activeAttemptOpt.get();
                    context.activeAttemptContext = activeRecord.context();
                    context.activeExpectedEvidence = activeRecord.expectedEvidence();
                    context.hintsRequestedForCurrentAttempt = activeRecord.hintsRequested();
                    context.isAttemptConsumed = false;

                    String prompt = context.activeQuestionPrompt;
                    if (prompt == null) {
                        QuestionDefinition def = questionBank.getQuestions().stream()
                                .filter(q -> q.questionId().equals(activeRecord.context().questionId()))
                                .findFirst().orElse(null);
                        prompt = def != null ? def.content() : "Question prompt: " + activeRecord.context().questionId();
                        context.activeQuestionPrompt = prompt;
                    }

                    return new QuestionResponse(
                            activeRecord.context().attemptId(),
                            sessionId,
                            context.topicId,
                            activeRecord.context().expectedState().name(),
                            activeRecord.context().difficulty().name(),
                            prompt,
                            activeRecord.hintsRequested(),
                            false
                    );
                }
            } else {
                // In-memory active attempt check
                if (context.activeAttemptContext != null && !context.isAttemptConsumed) {
                    return new QuestionResponse(
                            context.activeAttemptContext.attemptId(),
                            sessionId,
                            context.topicId,
                            context.activeAttemptContext.expectedState().name(),
                            context.activeAttemptContext.difficulty().name(),
                            context.activeQuestionPrompt,
                            context.hintsRequestedForCurrentAttempt,
                            false
                    );
                }
            }

            // Advance intro if needed
            if (flow.getCurrentState() == LearningState.CONTENT_INTRO) {
                boolean hasIntroQuestion = questionBank.getQuestions().stream()
                        .anyMatch(q -> q.topicId().equals(context.topicId) && q.targetStage() == LearningState.CONTENT_INTRO);
                if (!hasIntroQuestion) {
                    flow.advanceIntro();
                    if (isPersistenceEnabled()) {
                        SessionSnapshot currentSnap = sessionRepository.findById(UUID.fromString(sessionId)).orElse(null);
                        if (currentSnap != null) {
                            sessionRepository.save(new SessionSnapshot(
                                    currentSnap.sessionId(),
                                    currentSnap.learnerId(),
                                    currentSnap.topicId(),
                                    flow.getCurrentState().name(),
                                    flow.getNominalStageIndex(),
                                    flow.getPedagogicalFailures(),
                                    flow.getTotalAttemptsInState(),
                                    false,
                                    currentSnap.version(),
                                    currentSnap.createdAt(),
                                    Instant.now(),
                                    currentSnap.expiresAt()
                            ));
                        }
                    }
                }
            }

            LearningState currentObjective = flow.getCurrentState();
            GeneratedQuestion question = session.getNextQuestion(context.topicId, currentObjective);
            AttemptContext attemptContext = session.getActiveContextForTest();

            context.activeAttemptContext = attemptContext;
            context.activeQuestionPrompt = question.questionText();
            context.activeExpectedEvidence = question.expectedConcept();
            context.hintsRequestedForCurrentAttempt = 0;
            context.isAttemptConsumed = false;

            if (isPersistenceEnabled()) {
                AttemptRecord newRecord = new AttemptRecord(
                        attemptContext,
                        UUID.fromString(sessionId),
                        question.expectedConcept(),
                        0,
                        "ISSUED",
                        Instant.now(),
                        null
                );
                attemptRepository.save(newRecord);
            }

            return new QuestionResponse(
                    attemptContext.attemptId(),
                    sessionId,
                    context.topicId,
                    attemptContext.expectedState().name(),
                    attemptContext.difficulty().name(),
                    question.questionText(),
                    0,
                    false
            );
        } finally {
            context.lock.unlock();
        }
    }

    public HintResponse requestHint(String sessionId, String attemptId) {
        SessionContext context = getSessionContext(sessionId);
        context.lock.lock();
        try {
            validateActiveAttempt(context, attemptId);

            context.hintsRequestedForCurrentAttempt++;
            HintDto hint = hintGenerator.generateHint(
                    context.activeAttemptContext.questionId(),
                    context.activeExpectedEvidence,
                    context.hintsRequestedForCurrentAttempt
            );

            if (isPersistenceEnabled()) {
                UUID attemptUuid = UUID.fromString(attemptId);
                attemptRepository.findById(attemptUuid).ifPresent(record -> {
                    AttemptRecord updatedRecord = new AttemptRecord(
                            record.context(),
                            record.sessionId(),
                            record.expectedEvidence(),
                            context.hintsRequestedForCurrentAttempt,
                            record.status(),
                            record.createdAt(),
                            record.consumedAt()
                    );
                    attemptRepository.save(updatedRecord);
                });
            }

            return new HintResponse(
                    attemptId,
                    context.hintsRequestedForCurrentAttempt,
                    hint.hintText() != null ? hint.hintText() : "Hint unavailable."
            );
        } finally {
            context.lock.unlock();
        }
    }

    public SubmitAnswerResponse submitAnswer(String sessionId, String attemptId, String answer, Boolean codeTestsPassed) {
        SessionContext context = getSessionContext(sessionId);
        context.lock.lock();
        try {
            String requestFingerprint = SubmissionFingerprint.computeFingerprint(attemptId, answer);

            if (isPersistenceEnabled()) {
                UUID attemptUuid = UUID.fromString(attemptId);
                UUID sessionUuid = UUID.fromString(sessionId);

                AttemptRecord attempt = attemptRepository.findById(attemptUuid)
                        .orElseThrow(() -> new InvalidAttemptException("Attempt " + attemptId + " is not the active attempt for this session."));

                // Safeguard 2: Precise idempotent replay verification
                if ("CONSUMED".equals(attempt.status())) {
                    PersistedEvidence evidence = evidenceRepository.findByAttemptId(attemptUuid)
                            .orElseThrow(() -> new DuplicateAttemptException("Attempt " + attemptId + " has already been submitted."));

                    String storedFingerprint = evidence.idempotencyFingerprint() != null
                            ? evidence.idempotencyFingerprint()
                            : SubmissionFingerprint.extractFingerprint(evidence.feedback());
                    if (storedFingerprint != null && storedFingerprint.equals(requestFingerprint)) {
                        // Legitimate idempotent retry: return committed evaluation result
                        String cleanFeedback = SubmissionFingerprint.cleanFeedback(evidence.feedback());
                        String mastery = context.learnerProfile.getMastery(context.topicId).name();
                        return new SubmitAnswerResponse(
                                attemptId,
                                evidence.evidenceRecord().result().name(),
                                cleanFeedback,
                                evidence.evidenceRecord().misconceptionCategory(),
                                context.lessonFlow.getCurrentState().name(),
                                mastery,
                                false,
                                evidence.evidenceRecord().hintCount()
                        );
                    } else {
                        // Attempt ID reused with a different submission
                        throw new DuplicateAttemptException("Attempt " + attemptId + " has already been consumed with a different submission.");
                    }
                }

                if (!"ISSUED".equals(attempt.status())) {
                    throw new DuplicateAttemptException("Attempt " + attemptId + " is not active (status: " + attempt.status() + ").");
                }

                // Phase 1: Non-Transactional External AI Evaluation (ZERO DB connections held)
                int hintsUsed = attempt.hintsRequested();
                Set<String> validMisc = questionSelector.getValidMisconceptions(context.topicId);

                AIAssessment assessment = tutorEngine.assessAnswer(
                        attemptId,
                        context.topicId,
                        attempt.context().questionId(),
                        "dummy text",
                        attempt.expectedEvidence(),
                        answer,
                        attempt.context().allowedMisconceptions()
                );

                EvidenceRecord candidateEvidence = EvidencePolicy.authorize(
                        assessment,
                        attempt.context(),
                        hintsUsed,
                        false, // client claims untrusted
                        validMisc
                );

                // Handle provider failures without burning attempt or opening transactions
                if (candidateEvidence.result() == EvaluationResult.PROVIDER_FAILURE || candidateEvidence.result() == EvaluationResult.PROVIDER_TIMEOUT) {
                    boolean isTimeout = candidateEvidence.result() == EvaluationResult.PROVIDER_TIMEOUT;
                    throw new AIProviderUnavailableException(
                            "AI evaluation service is temporarily unavailable. Please retry your submission.",
                            isTimeout
                    );
                }

                // Phase 2: Transactional Atomic Commit via TransactionalSubmissionService
                SessionSnapshot currentSession = sessionRepository.findById(sessionUuid)
                        .orElseThrow(() -> new SessionNotFoundException("Session not found: " + sessionId));

                LessonFlow detachedFlow = LessonFlow.rehydrate(
                        context.topicDef,
                        context.topicTemplate,
                        context.learnerProfile,
                        masteryEngine,
                        context.lessonFlow.getCurrentState(),
                        context.lessonFlow.getNominalStageIndex(),
                        context.lessonFlow.getPedagogicalFailures(),
                        context.lessonFlow.getTotalAttemptsInState(),
                        context.lessonFlow.getHistory()
                );

                String feedbackText = "Evaluation completed: " + candidateEvidence.result().name();
                TransactionalSubmissionService.TransactionalCommitResult commitResult = transactionalSubmissionService.commitSubmission(
                        sessionUuid,
                        attemptUuid,
                        context.learnerId,
                        context.topicId,
                        candidateEvidence,
                        feedbackText,
                        requestFingerprint,
                        currentSession.version(),
                        detachedFlow
                );

                // Safeguard 3: Update in-memory state ONLY post-commit
                context.lessonFlow.submitEvidence(candidateEvidence);
                context.learningSession.submitAnswer(
                        attempt.expectedEvidence(),
                        answer,
                        hintsUsed,
                        false
                );
                context.isAttemptConsumed = true;
                context.activeAttemptFingerprint = requestFingerprint;

                SubmitAnswerResponse response = new SubmitAnswerResponse(
                        attemptId,
                        commitResult.evidence().result().name(),
                        feedbackText,
                        commitResult.evidence().misconceptionCategory(),
                        commitResult.nextState(),
                        commitResult.masteryLevel(),
                        commitResult.stageAdvanced(),
                        commitResult.hintsUsed()
                );
                context.lastResponse = response;
                return response;

            } else {
                // In-Memory Mode: preserve existing baseline contract (subsequent submissions return 409)
                validateActiveAttempt(context, attemptId);

                AttemptContext savedAttempt = context.activeAttemptContext;
                int hintsUsed = context.hintsRequestedForCurrentAttempt;

                EvidenceRecord evidence = context.learningSession.submitAnswer(
                        context.activeExpectedEvidence,
                        answer,
                        hintsUsed,
                        false
                );

                if (evidence.result() == EvaluationResult.PROVIDER_FAILURE || evidence.result() == EvaluationResult.PROVIDER_TIMEOUT) {
                    context.learningSession.overrideContext(savedAttempt);
                    boolean isTimeout = evidence.result() == EvaluationResult.PROVIDER_TIMEOUT;
                    throw new AIProviderUnavailableException(
                            "AI evaluation service is temporarily unavailable. Please retry your submission.",
                            isTimeout
                    );
                }

                context.isAttemptConsumed = true;
                context.activeAttemptFingerprint = requestFingerprint;

                LearningState priorState = context.lessonFlow.getCurrentState();
                context.lessonFlow.submitEvidence(evidence);
                LearningState nextState = context.lessonFlow.getCurrentState();
                boolean stageAdvanced = priorState != nextState && evidence.result() == EvaluationResult.PASS;

                String mastery = context.learnerProfile.getMastery(context.topicId).name();
                SubmitAnswerResponse response = new SubmitAnswerResponse(
                        attemptId,
                        evidence.result().name(),
                        "Evaluation completed: " + evidence.result().name(),
                        evidence.misconceptionCategory(),
                        nextState.name(),
                        mastery,
                        stageAdvanced,
                        hintsUsed
                );
                context.lastResponse = response;
                return response;
            }
        } finally {
            context.lock.unlock();
        }
    }

    private void validateActiveAttempt(SessionContext context, String attemptId) {
        if (context.activeAttemptContext == null || !context.activeAttemptContext.attemptId().equals(attemptId)) {
            throw new InvalidAttemptException("Attempt " + attemptId + " is not the active attempt for this session.");
        }
        if (context.isAttemptConsumed) {
            throw new DuplicateAttemptException("Attempt " + attemptId + " has already been submitted.");
        }
    }

    private SessionContext getSessionContext(String sessionId) {
        SessionContext context = sessionCache.getIfPresent(sessionId);
        if (context == null && isPersistenceEnabled()) {
            try {
                UUID sessionUuid = UUID.fromString(sessionId);
                SessionSnapshot snapshot = sessionRepository.findById(sessionUuid).orElse(null);
                if (snapshot != null) {
                    return rehydrateSessionContext(snapshot);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (context == null) {
            throw new SessionNotFoundException("Session not found: " + sessionId);
        }
        if (context.isExpired(Instant.now())) {
            throw new SessionExpiredException("Session " + sessionId + " has expired.");
        }
        return context;
    }

    private SessionContext rehydrateSessionContext(SessionSnapshot snapshot) {
        if (snapshot.expiresAt() != null && snapshot.expiresAt().isBefore(Instant.now())) {
            throw new SessionExpiredException("Session " + snapshot.sessionId() + " has expired.");
        }

        String topicId = snapshot.topicId();
        TopicDefinition topicDef = curriculumGraph.getDefinition(topicId);
        TopicTemplate topicTemplate = TopicLoader.load(topicId);
        LearnerProfile profile = new LearnerProfile(snapshot.learnerId());

        List<EvidenceRecord> history = evidenceRepository != null
                ? evidenceRepository.findChronologicalHistory(snapshot.learnerId(), topicId)
                : Collections.emptyList();

        profile.getTopicState(topicId).evaluateFromHistory(history);

        LearningState activeState = LearningState.valueOf(snapshot.currentState());
        LessonFlow lessonFlow = LessonFlow.rehydrate(
                topicDef,
                topicTemplate,
                profile,
                masteryEngine,
                activeState,
                snapshot.nominalStageIndex(),
                snapshot.pedagogicalFailures(),
                snapshot.totalAttemptsInState(),
                history
        );

        AttemptContext activeAttemptContext = null;
        String activeQuestionPrompt = null;
        String activeExpectedEvidence = null;
        int hintsRequested = 0;
        boolean isAttemptConsumed = true;

        if (attemptRepository != null) {
            Optional<AttemptRecord> activeOpt = attemptRepository.findActiveAttemptBySessionId(snapshot.sessionId());
            if (activeOpt.isPresent()) {
                AttemptRecord rec = activeOpt.get();
                activeAttemptContext = rec.context();
                activeExpectedEvidence = rec.expectedEvidence();
                hintsRequested = rec.hintsRequested();
                isAttemptConsumed = false;

                QuestionDefinition qDef = questionBank.getQuestions().stream()
                        .filter(q -> q.questionId().equals(rec.context().questionId()))
                        .findFirst().orElse(null);
                if (qDef != null) {
                    activeQuestionPrompt = qDef.content();
                }
            }
        }

        LearningSession learningSession = LearningSession.rehydrate(
                profile,
                masteryEngine,
                questionSelector,
                tutorEngine,
                history,
                activeAttemptContext,
                !snapshot.isCompleted()
        );

        SessionContext context = new SessionContext(
                snapshot.sessionId().toString(),
                snapshot.learnerId(),
                topicId,
                topicDef,
                topicTemplate,
                profile,
                learningSession,
                lessonFlow,
                snapshot.createdAt(),
                snapshot.expiresAt()
        );
        context.activeAttemptContext = activeAttemptContext;
        context.activeQuestionPrompt = activeQuestionPrompt;
        context.activeExpectedEvidence = activeExpectedEvidence;
        context.hintsRequestedForCurrentAttempt = hintsRequested;
        context.isAttemptConsumed = isAttemptConsumed;

        sessionCache.put(context.sessionId, context);
        return context;
    }

    private SessionResponse toSessionResponse(SessionContext context) {
        LearningState state = context.lessonFlow.getCurrentState();
        boolean completed = state == LearningState.COMPLETED || state == LearningState.FAILED_TERMINATED;
        String mastery = context.learnerProfile.getMastery(context.topicId).name();
        int totalStages = context.topicDef.stageRequirements() != null ? context.topicDef.stageRequirements().size() : 9;

        return new SessionResponse(
                context.sessionId,
                context.learnerId,
                context.topicId,
                context.topicDef.title(),
                state.name(),
                mastery,
                totalStages,
                completed
        );
    }

    // Public method for tests to clear in-memory cache and test recovery from PostgreSQL
    public void clearInMemoryCache() {
        sessionCache.invalidateAll();
    }

    public long getCachedSessionCount() {
        return sessionCache.estimatedSize();
    }

    public void cleanUpCache() {
        sessionCache.cleanUp();
    }

    public int reapExpiredCachedSessions(Instant now) {
        int reaped = 0;
        for (Map.Entry<String, SessionContext> entry : sessionCache.asMap().entrySet()) {
            if (entry.getValue().isExpired(now)) {
                sessionCache.invalidate(entry.getKey());
                reaped++;
            }
        }
        return reaped;
    }

    public void expireSessionForTesting(String sessionId) {
        SessionContext context = sessionCache.getIfPresent(sessionId);
        if (context != null) {
            SessionContext expired = new SessionContext(
                    context.sessionId, context.learnerId, context.topicId,
                    context.topicDef, context.topicTemplate, context.learnerProfile,
                    context.learningSession, context.lessonFlow,
                    context.createdAt, Instant.now().minusSeconds(60)
            );
            expired.activeAttemptContext = context.activeAttemptContext;
            expired.activeQuestionPrompt = context.activeQuestionPrompt;
            expired.activeExpectedEvidence = context.activeExpectedEvidence;
            expired.hintsRequestedForCurrentAttempt = context.hintsRequestedForCurrentAttempt;
            expired.isAttemptConsumed = context.isAttemptConsumed;
            sessionCache.put(sessionId, expired);
        }
    }

    public static class SessionContext {
        final String sessionId;
        final String learnerId;
        final String topicId;
        final TopicDefinition topicDef;
        final TopicTemplate topicTemplate;
        final LearnerProfile learnerProfile;
        final LearningSession learningSession;
        final LessonFlow lessonFlow;
        final ReentrantLock lock = new ReentrantLock();

        AttemptContext activeAttemptContext;
        String activeQuestionPrompt;
        String activeExpectedEvidence;
        int hintsRequestedForCurrentAttempt = 0;
        boolean isAttemptConsumed = false;
        String activeAttemptFingerprint;
        SubmitAnswerResponse lastResponse;
        final Instant createdAt;
        final Instant expiresAt;

        SessionContext(
                String sessionId,
                String learnerId,
                String topicId,
                TopicDefinition topicDef,
                TopicTemplate topicTemplate,
                LearnerProfile learnerProfile,
                LearningSession learningSession,
                LessonFlow lessonFlow,
                Instant createdAt,
                Instant expiresAt) {
            this.sessionId = sessionId;
            this.learnerId = learnerId;
            this.topicId = topicId;
            this.topicDef = topicDef;
            this.topicTemplate = topicTemplate;
            this.learnerProfile = learnerProfile;
            this.learningSession = learningSession;
            this.lessonFlow = lessonFlow;
            this.createdAt = createdAt != null ? createdAt : Instant.now();
            this.expiresAt = expiresAt != null ? expiresAt : this.createdAt.plus(24, ChronoUnit.HOURS);
        }

        SessionContext(
                String sessionId,
                String learnerId,
                String topicId,
                TopicDefinition topicDef,
                TopicTemplate topicTemplate,
                LearnerProfile learnerProfile,
                LearningSession learningSession,
                LessonFlow lessonFlow) {
            this(sessionId, learnerId, topicId, topicDef, topicTemplate,
                 learnerProfile, learningSession, lessonFlow, Instant.now(), Instant.now().plus(24, ChronoUnit.HOURS));
        }

        public boolean isExpired(Instant now) {
            return expiresAt != null && !now.isBefore(expiresAt);
        }
    }
}
