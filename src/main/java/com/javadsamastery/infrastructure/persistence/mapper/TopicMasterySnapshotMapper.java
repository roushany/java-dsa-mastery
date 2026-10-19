package com.javadsamastery.infrastructure.persistence.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javadsamastery.domain.ports.TopicMasteryRepositoryPort.MasterySnapshotRecord;
import com.javadsamastery.infrastructure.persistence.entity.TopicMasterySnapshotEntity;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;

public class TopicMasterySnapshotMapper {

    private final ObjectMapper objectMapper;

    public TopicMasterySnapshotMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public TopicMasterySnapshotEntity toEntity(MasterySnapshotRecord record) {
        if (record == null) return null;
        String misconceptionsJson = serializeSet(record.activeMisconceptions());

        return new TopicMasterySnapshotEntity(
            record.learnerId(),
            record.topicId(),
            record.masteryLevel(),
            record.independentConceptPasses(),
            record.independentImplementationPasses(),
            record.transferPasses(),
            record.retrievalPasses(),
            record.consecutiveFailures(),
            misconceptionsJson,
            record.updatedAt(),
            record.version()
        );
    }

    public MasterySnapshotRecord toDomain(TopicMasterySnapshotEntity entity) {
        if (entity == null) return null;
        Set<String> misconceptions = deserializeSet(entity.getActiveMisconceptions());

        return new MasterySnapshotRecord(
            entity.getLearnerId(),
            entity.getTopicId(),
            entity.getMasteryLevel(),
            entity.getIndependentConceptPasses(),
            entity.getIndependentImplementationPasses(),
            entity.getTransferPasses(),
            entity.getRetrievalPasses(),
            entity.getConsecutiveFailures(),
            misconceptions,
            entity.getUpdatedAt(),
            entity.getVersion() != null ? entity.getVersion() : 0L
        );
    }

    private String serializeSet(Set<String> set) {
        if (set == null || set.isEmpty()) return "[]";
        try {
            return objectMapper.writeValueAsString(set);
        } catch (Exception e) {
            return "[]";
        }
    }

    private Set<String> deserializeSet(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) return Collections.emptySet();
        try {
            return objectMapper.readValue(json, new TypeReference<Set<String>>() {});
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }
}
