package com.javadsamastery.infrastructure.persistence.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javadsamastery.domain.evidence.AttemptContext;
import com.javadsamastery.domain.evidence.EvaluationResult;
import com.javadsamastery.domain.evidence.EvidenceRecord;
import com.javadsamastery.domain.lesson.LearningState;
import com.javadsamastery.domain.lesson.QuestionType;
import com.javadsamastery.domain.ports.AttemptRepositoryPort.AttemptRecord;
import com.javadsamastery.domain.ports.EvidenceRepositoryPort.PersistedEvidence;
import com.javadsamastery.domain.ports.SessionRepositoryPort.SessionSnapshot;
import com.javadsamastery.domain.ports.TopicMasteryRepositoryPort.MasterySnapshotRecord;
import com.javadsamastery.domain.problem.ProblemDifficulty;
import com.javadsamastery.infrastructure.persistence.entity.AttemptEntity;
import com.javadsamastery.infrastructure.persistence.entity.EvidenceEntity;
import com.javadsamastery.infrastructure.persistence.entity.SessionEntity;
import com.javadsamastery.infrastructure.persistence.entity.TopicMasterySnapshotEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PersistenceMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AttemptEntityMapper attemptMapper = new AttemptEntityMapper(objectMapper);
    private final EvidenceEntityMapper evidenceMapper = new EvidenceEntityMapper();
    private final TopicMasterySnapshotMapper snapshotMapper = new TopicMasterySnapshotMapper(objectMapper);
    private final SessionEntityMapper sessionMapper = new SessionEntityMapper();

    @Test
    @DisplayName("AttemptEntityMapper maps bidirectional context with JSON misconceptions")
    void testAttemptMapper_Bidirectional() {
        UUID attemptId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        AttemptContext ctx = new AttemptContext(
            attemptId.toString(),
            "binary-search",
            "bs-01",
            LearningState.CONCEPT_ASSESSMENT,
            QuestionType.CONCEPT,
            ProblemDifficulty.MEDIUM,
            "learner-123",
            List.of("OFF_BY_ONE", "WRONG_MID_FORMULA")
        );
        AttemptRecord record = new AttemptRecord(
            ctx,
            sessionId,
            "Expected invariant check",
            1,
            "ISSUED",
            Instant.now(),
            null
        );

        AttemptEntity entity = attemptMapper.toEntity(record);
        assertNotNull(entity);
        assertEquals(attemptId, entity.getAttemptId());
        assertEquals("learner-123", entity.getLearnerId());
        assertEquals("binary-search", entity.getTopicId());
        assertEquals("ISSUED", entity.getStatus());
        assertTrue(entity.getAllowedMisconceptions().contains("OFF_BY_ONE"));

        AttemptRecord roundTrip = attemptMapper.toDomain(entity);
        assertNotNull(roundTrip);
        assertEquals(ctx.attemptId(), roundTrip.context().attemptId());
        assertEquals(ctx.topicId(), roundTrip.context().topicId());
        assertEquals(ctx.expectedState(), roundTrip.context().expectedState());
        assertEquals(2, roundTrip.context().allowedMisconceptions().size());
        assertEquals("ISSUED", roundTrip.status());
    }

    @Test
    @DisplayName("EvidenceEntityMapper maps bidirectional evidence record with timestamp preservation")
    void testEvidenceMapper_Bidirectional() {
        UUID attemptId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();

        EvidenceRecord domainRecord = new EvidenceRecord(
            "learner-123",
            "two-pointers",
            "tp-q1",
            ProblemDifficulty.HARD,
            QuestionType.IMPLEMENTATION,
            2,
            true,
            EvaluationResult.PASS,
            null,
            now
        );

        EvidenceEntity entity = evidenceMapper.toEntity(domainRecord, attemptId, sessionId, "Great job!", "deadbeef1234");
        assertNotNull(entity);
        assertEquals(attemptId, entity.getAttemptId());
        assertEquals(sessionId, entity.getSessionId());
        assertEquals("learner-123", entity.getLearnerId());
        assertEquals("two-pointers", entity.getTopicId());
        assertEquals("tp-q1", entity.getQuestionId());
        assertEquals("PASS", entity.getEvaluationResult());
        assertTrue(entity.isRetrieval());
        assertEquals("Great job!", entity.getFeedback());
        assertEquals("deadbeef1234", entity.getIdempotencyFingerprint());

        EvidenceRecord roundTrip = evidenceMapper.toDomain(entity);
        assertEquals(domainRecord.learnerId(), roundTrip.learnerId());
        assertEquals(domainRecord.topicId(), roundTrip.topicId());
        assertEquals(domainRecord.problemId(), roundTrip.problemId());
        assertEquals(domainRecord.result(), roundTrip.result());
        assertEquals(domainRecord.hintCount(), roundTrip.hintCount());
        assertEquals(domainRecord.isRetrievalPractice(), roundTrip.isRetrievalPractice());

        PersistedEvidence persisted = evidenceMapper.toPersistedDomain(entity);
        assertEquals(attemptId, persisted.attemptId());
        assertEquals("Great job!", persisted.feedback());
        assertEquals("deadbeef1234", persisted.idempotencyFingerprint());
    }

    @Test
    @DisplayName("TopicMasterySnapshotMapper maps bidirectional mastery snapshot with JSON active misconceptions")
    void testSnapshotMapper_Bidirectional() {
        Instant now = Instant.now();
        MasterySnapshotRecord domainRecord = new MasterySnapshotRecord(
            "learner-123",
            "sliding-window",
            "PROFICIENT",
            3,
            2,
            1,
            1,
            0,
            Set.of("INCORRECT_SHRINK_CONDITION"),
            now,
            5L
        );

        TopicMasterySnapshotEntity entity = snapshotMapper.toEntity(domainRecord);
        assertNotNull(entity);
        assertEquals("learner-123", entity.getLearnerId());
        assertEquals("sliding-window", entity.getTopicId());
        assertEquals("PROFICIENT", entity.getMasteryLevel());
        assertEquals(3, entity.getIndependentConceptPasses());
        assertEquals(5L, entity.getVersion());
        assertTrue(entity.getActiveMisconceptions().contains("INCORRECT_SHRINK_CONDITION"));

        MasterySnapshotRecord roundTrip = snapshotMapper.toDomain(entity);
        assertEquals("learner-123", roundTrip.learnerId());
        assertEquals("sliding-window", roundTrip.topicId());
        assertEquals("PROFICIENT", roundTrip.masteryLevel());
        assertEquals(1, roundTrip.activeMisconceptions().size());
        assertTrue(roundTrip.activeMisconceptions().contains("INCORRECT_SHRINK_CONDITION"));
        assertEquals(5L, roundTrip.version());
    }

    @Test
    @DisplayName("SessionEntityMapper maps bidirectional session snapshot accurately")
    void testSessionMapper_Bidirectional() {
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant expires = now.plusSeconds(3600);

        SessionSnapshot domainSnapshot = new SessionSnapshot(
            sessionId,
            "learner-999",
            "prefix-sum",
            "IMPLEMENTATION",
            3,
            1,
            2,
            false,
            7L,
            now,
            now,
            expires
        );

        SessionEntity entity = sessionMapper.toEntity(domainSnapshot);
        assertNotNull(entity);
        assertEquals(sessionId, entity.getSessionId());
        assertEquals("learner-999", entity.getLearnerId());
        assertEquals("prefix-sum", entity.getTopicId());
        assertEquals("IMPLEMENTATION", entity.getCurrentState());
        assertEquals(3, entity.getNominalStageIndex());
        assertEquals(7L, entity.getVersion());

        SessionSnapshot roundTrip = sessionMapper.toDomain(entity);
        assertEquals(sessionId, roundTrip.sessionId());
        assertEquals("learner-999", roundTrip.learnerId());
        assertEquals("prefix-sum", roundTrip.topicId());
        assertEquals("IMPLEMENTATION", roundTrip.currentState());
        assertEquals(3, roundTrip.nominalStageIndex());
        assertEquals(7L, roundTrip.version());
        assertFalse(roundTrip.isCompleted());
    }
}
