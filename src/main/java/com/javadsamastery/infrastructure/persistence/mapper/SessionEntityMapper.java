package com.javadsamastery.infrastructure.persistence.mapper;

import com.javadsamastery.domain.ports.SessionRepositoryPort.SessionSnapshot;
import com.javadsamastery.infrastructure.persistence.entity.SessionEntity;

public class SessionEntityMapper {

    public SessionEntity toEntity(SessionSnapshot snapshot) {
        if (snapshot == null) return null;
        return new SessionEntity(
            snapshot.sessionId(),
            snapshot.learnerId(),
            snapshot.topicId(),
            snapshot.currentState(),
            snapshot.nominalStageIndex(),
            snapshot.pedagogicalFailures(),
            snapshot.totalAttemptsInState(),
            snapshot.isCompleted(),
            snapshot.version(),
            snapshot.createdAt(),
            snapshot.updatedAt(),
            snapshot.expiresAt()
        );
    }

    public SessionSnapshot toDomain(SessionEntity entity) {
        if (entity == null) return null;
        return new SessionSnapshot(
            entity.getSessionId(),
            entity.getLearnerId(),
            entity.getTopicId(),
            entity.getCurrentState(),
            entity.getNominalStageIndex(),
            entity.getPedagogicalFailures(),
            entity.getTotalAttemptsInState(),
            entity.isCompleted(),
            entity.getVersion() != null ? entity.getVersion() : 0L,
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getExpiresAt()
        );
    }
}
