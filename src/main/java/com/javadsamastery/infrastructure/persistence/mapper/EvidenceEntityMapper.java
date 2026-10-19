package com.javadsamastery.infrastructure.persistence.mapper;

import com.javadsamastery.domain.evidence.EvaluationResult;
import com.javadsamastery.domain.evidence.EvidenceRecord;
import com.javadsamastery.domain.lesson.QuestionType;
import com.javadsamastery.domain.ports.EvidenceRepositoryPort.PersistedEvidence;
import com.javadsamastery.domain.problem.ProblemDifficulty;
import com.javadsamastery.infrastructure.persistence.entity.EvidenceEntity;

import java.util.UUID;

public class EvidenceEntityMapper {

    public EvidenceEntity toEntity(EvidenceRecord record, UUID attemptId, UUID sessionId, String feedback, String idempotencyFingerprint) {
        if (record == null) return null;
        return new EvidenceEntity(
            attemptId,
            sessionId,
            record.learnerId(),
            record.topicId(),
            record.problemId(),
            record.questionType().name(),
            record.difficulty().name(),
            record.hintCount(),
            record.isRetrievalPractice(),
            record.result().name(),
            record.misconceptionCategory(),
            feedback,
            idempotencyFingerprint,
            record.timestamp()
        );
    }

    public EvidenceEntity toEntity(EvidenceRecord record, UUID attemptId, UUID sessionId, String feedback) {
        return toEntity(record, attemptId, sessionId, feedback, null);
    }

    public EvidenceRecord toDomain(EvidenceEntity entity) {
        if (entity == null) return null;
        return new EvidenceRecord(
            entity.getLearnerId(),
            entity.getTopicId(),
            entity.getQuestionId(),
            ProblemDifficulty.valueOf(entity.getDifficulty()),
            QuestionType.valueOf(entity.getQuestionType()),
            entity.getHintsUsed(),
            entity.isRetrieval(),
            EvaluationResult.valueOf(entity.getEvaluationResult()),
            entity.getMisconceptionCategory(),
            entity.getCreatedAt()
        );
    }

    public PersistedEvidence toPersistedDomain(EvidenceEntity entity) {
        if (entity == null) return null;
        EvidenceRecord domainRecord = toDomain(entity);
        return new PersistedEvidence(
            entity.getEvidenceId(),
            domainRecord,
            entity.getAttemptId(),
            entity.getSessionId(),
            entity.getFeedback(),
            entity.getIdempotencyFingerprint()
        );
    }
}
