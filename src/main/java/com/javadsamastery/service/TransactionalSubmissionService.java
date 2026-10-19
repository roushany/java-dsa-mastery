package com.javadsamastery.service;

import com.javadsamastery.domain.evidence.EvaluationResult;
import com.javadsamastery.domain.evidence.EvidenceRecord;
import com.javadsamastery.domain.learner.LearnerProfile;
import com.javadsamastery.domain.learner.TopicState;
import com.javadsamastery.domain.lesson.LearningState;
import com.javadsamastery.domain.ports.AttemptRepositoryPort;
import com.javadsamastery.domain.ports.AttemptRepositoryPort.AttemptRecord;
import com.javadsamastery.domain.ports.EvidenceRepositoryPort;
import com.javadsamastery.domain.ports.SessionRepositoryPort;
import com.javadsamastery.domain.ports.SessionRepositoryPort.SessionSnapshot;
import com.javadsamastery.domain.ports.TopicMasteryRepositoryPort;
import com.javadsamastery.domain.ports.TopicMasteryRepositoryPort.MasterySnapshotRecord;
import com.javadsamastery.engine.MasteryEngine;
import com.javadsamastery.service.exception.DuplicateAttemptException;
import com.javadsamastery.service.exception.InvalidAttemptException;
import com.javadsamastery.service.exception.SessionExpiredException;
import com.javadsamastery.service.exception.SessionNotFoundException;
import com.javadsamastery.service.exception.StaleSessionVersionException;
import com.javadsamastery.service.util.SubmissionFingerprint;
import com.javadsamastery.session.LessonFlow;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "javadsa.persistence.enabled", havingValue = "true")
public class TransactionalSubmissionService {

    private final AttemptRepositoryPort attemptRepository;
    private final SessionRepositoryPort sessionRepository;
    private final EvidenceRepositoryPort evidenceRepository;
    private final TopicMasteryRepositoryPort topicMasteryRepository;
    private final MasteryEngine masteryEngine;

    public TransactionalSubmissionService(
            AttemptRepositoryPort attemptRepository,
            SessionRepositoryPort sessionRepository,
            EvidenceRepositoryPort evidenceRepository,
            TopicMasteryRepositoryPort topicMasteryRepository,
            MasteryEngine masteryEngine) {
        this.attemptRepository = attemptRepository;
        this.sessionRepository = sessionRepository;
        this.evidenceRepository = evidenceRepository;
        this.topicMasteryRepository = topicMasteryRepository;
        this.masteryEngine = masteryEngine;
    }

    public record TransactionalCommitResult(
            EvidenceRecord evidence,
            String nextState,
            String masteryLevel,
            boolean stageAdvanced,
            long newSessionVersion,
            int hintsUsed
    ) {}

    /**
     * Executes Phase 2 atomic commit inside an isolated database transaction.
     * Enforces strict lock ordering:
     * 1. Revalidates and locks session row via optimistic version check.
     * 2. Revalidates attempt context and transitions attempt from ISSUED to CONSUMED.
     * 3. Locks or initializes topic mastery snapshot row via FOR UPDATE.
     * 4. Inserts authoritative evidence record.
     * 5. Advances LessonFlow and persists updated session snapshot.
     * 6. Recalculates topic mastery from chronological history and persists updated snapshot.
     */
    @Transactional
    public TransactionalCommitResult commitSubmission(
            UUID sessionId,
            UUID attemptId,
            String learnerId,
            String topicId,
            EvidenceRecord candidateEvidence,
            String actualFeedback,
            String fingerprint,
            long expectedSessionVersion,
            LessonFlow detachedLessonFlow) {

        // 1. REVALIDATE ATTEMPT inside transaction
        AttemptRecord attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new InvalidAttemptException("Attempt not found: " + attemptId));

        if (!sessionId.equals(attempt.sessionId())) {
            throw new InvalidAttemptException("Attempt " + attemptId + " does not belong to session " + sessionId);
        }
        if (!learnerId.equals(attempt.context().learnerId()) || !topicId.equals(attempt.context().topicId())) {
            throw new InvalidAttemptException("Attempt context mismatch for attempt " + attemptId);
        }
        if (!"ISSUED".equals(attempt.status())) {
            throw new DuplicateAttemptException("Attempt " + attemptId + " is not in ISSUED status (current: " + attempt.status() + ")");
        }

        // 2. REVALIDATE SESSION inside transaction (Consistent lock order: session first)
        SessionSnapshot sessionSnapshot = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("Session not found: " + sessionId));

        if (sessionSnapshot.isCompleted()) {
            throw new IllegalStateException("Session is already completed: " + sessionId);
        }
        if (sessionSnapshot.expiresAt() != null && sessionSnapshot.expiresAt().isBefore(Instant.now())) {
            throw new SessionExpiredException("Session has expired: " + sessionId);
        }
        if (sessionSnapshot.version() != expectedSessionVersion) {
            throw new StaleSessionVersionException(
                    "Session version mismatch (expected: " + expectedSessionVersion + ", actual: " + sessionSnapshot.version() + ")");
        }

        // 3. LOCK MASTERY SNAPSHOT (Consistent lock order: mastery snapshot second)
        MasterySnapshotRecord lockedMastery = topicMasteryRepository.lockOrInitialize(learnerId, topicId);

        // 4. ATOMICALLY CONSUME ATTEMPT (Conditional update ensures single-use guarantee under concurrency)
        boolean consumed = attemptRepository.consumeAttempt(attemptId);
        if (!consumed) {
            throw new DuplicateAttemptException("Attempt " + attemptId + " was already consumed by a concurrent transaction.");
        }

        // 5. INSERT AUTHORITATIVE EVIDENCE RECORD (Composite FK validates attempt context)
        evidenceRepository.save(candidateEvidence, attemptId, sessionId, actualFeedback, fingerprint);

        // 6. ADVANCE DETACHED LESSON FLOW & PERSIST UPDATED SESSION
        LearningState priorState = detachedLessonFlow.getCurrentState();
        detachedLessonFlow.submitEvidence(candidateEvidence);
        LearningState nextState = detachedLessonFlow.getCurrentState();
        boolean stageAdvanced = priorState != nextState && candidateEvidence.result() == EvaluationResult.PASS;
        boolean isCompleted = nextState == LearningState.COMPLETED || nextState == LearningState.FAILED_TERMINATED;

        SessionSnapshot updatedSession = new SessionSnapshot(
                sessionId,
                learnerId,
                topicId,
                nextState.name(),
                detachedLessonFlow.getNominalStageIndex(),
                detachedLessonFlow.getPedagogicalFailures(),
                detachedLessonFlow.getTotalAttemptsInState(),
                isCompleted,
                expectedSessionVersion,
                sessionSnapshot.createdAt(),
                Instant.now(),
                sessionSnapshot.expiresAt()
        );
        sessionRepository.save(updatedSession);

        // 7. RECALCULATE TOPIC MASTERY FROM CHRONOLOGICAL HISTORY & PERSIST SNAPSHOT
        List<EvidenceRecord> allEvidence = evidenceRepository.findChronologicalHistory(learnerId, topicId);
        LearnerProfile tempProfile = new LearnerProfile(learnerId);
        masteryEngine.evaluateMastery(tempProfile, topicId, allEvidence);
        TopicState recalculatedState = tempProfile.getTopicState(topicId);

        MasterySnapshotRecord updatedMastery = new MasterySnapshotRecord(
                learnerId,
                topicId,
                recalculatedState.getMastery().name(),
                recalculatedState.getIndependentConceptPasses(),
                recalculatedState.getIndependentImplementationPasses(),
                recalculatedState.getTransferPasses(),
                recalculatedState.getRetrievalPasses(),
                recalculatedState.getConsecutiveFailures(),
                recalculatedState.getActiveMisconceptions(),
                Instant.now(),
                lockedMastery.version()
        );
        topicMasteryRepository.saveSnapshot(updatedMastery);

        return new TransactionalCommitResult(
                candidateEvidence,
                nextState.name(),
                recalculatedState.getMastery().name(),
                stageAdvanced,
                expectedSessionVersion + 1,
                candidateEvidence.hintCount()
        );
    }
}
