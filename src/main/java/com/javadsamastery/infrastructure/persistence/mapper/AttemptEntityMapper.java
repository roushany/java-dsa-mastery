package com.javadsamastery.infrastructure.persistence.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javadsamastery.domain.evidence.AttemptContext;
import com.javadsamastery.domain.lesson.LearningState;
import com.javadsamastery.domain.lesson.QuestionType;
import com.javadsamastery.domain.ports.AttemptRepositoryPort.AttemptRecord;
import com.javadsamastery.domain.problem.ProblemDifficulty;
import com.javadsamastery.infrastructure.persistence.entity.AttemptEntity;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class AttemptEntityMapper {

    private final ObjectMapper objectMapper;

    public AttemptEntityMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public AttemptEntity toEntity(AttemptRecord domainRecord) {
        if (domainRecord == null) return null;
        AttemptContext ctx = domainRecord.context();
        String misconceptionsJson = serializeList(ctx.allowedMisconceptions());

        return new AttemptEntity(
            UUID.fromString(ctx.attemptId()),
            domainRecord.sessionId(),
            ctx.learnerId(),
            ctx.topicId(),
            ctx.questionId(),
            ctx.expectedState().name(),
            ctx.questionType().name(),
            ctx.difficulty().name(),
            domainRecord.expectedEvidence(),
            misconceptionsJson,
            domainRecord.hintsRequested(),
            domainRecord.status(),
            domainRecord.createdAt(),
            domainRecord.consumedAt()
        );
    }

    public AttemptRecord toDomain(AttemptEntity entity) {
        if (entity == null) return null;
        List<String> misconceptions = deserializeList(entity.getAllowedMisconceptions());

        AttemptContext ctx = new AttemptContext(
            entity.getAttemptId().toString(),
            entity.getTopicId(),
            entity.getQuestionId(),
            LearningState.valueOf(entity.getTargetStage()),
            QuestionType.valueOf(entity.getQuestionType()),
            ProblemDifficulty.valueOf(entity.getDifficulty()),
            entity.getLearnerId(),
            misconceptions
        );

        return new AttemptRecord(
            ctx,
            entity.getSessionId(),
            entity.getExpectedEvidence(),
            entity.getHintsRequested(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getConsumedAt()
        );
    }

    private String serializeList(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> deserializeList(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
