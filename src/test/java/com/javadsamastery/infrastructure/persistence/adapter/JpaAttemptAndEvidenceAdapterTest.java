package com.javadsamastery.infrastructure.persistence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javadsamastery.domain.evidence.EvaluationResult;
import com.javadsamastery.domain.evidence.EvidenceRecord;
import com.javadsamastery.domain.lesson.QuestionType;
import com.javadsamastery.domain.ports.AttemptRepositoryPort.AttemptRecord;
import com.javadsamastery.domain.problem.ProblemDifficulty;
import com.javadsamastery.infrastructure.persistence.entity.AttemptEntity;
import com.javadsamastery.infrastructure.persistence.entity.EvidenceEntity;
import com.javadsamastery.infrastructure.persistence.repository.SpringDataAttemptRepository;
import com.javadsamastery.infrastructure.persistence.repository.SpringDataEvidenceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JpaAttemptAndEvidenceAdapterTest {

    @Test
    @DisplayName("consumeAttempt returns true on successful atomic update and false when attempt is already consumed")
    void testConsumeAttempt() {
        SpringDataAttemptRepository mockRepo = mock(SpringDataAttemptRepository.class);
        JpaAttemptRepositoryAdapter adapter = new JpaAttemptRepositoryAdapter(mockRepo, new ObjectMapper());

        UUID attemptId = UUID.randomUUID();
        when(mockRepo.consumeAttempt(eq(attemptId), any(Instant.class))).thenReturn(1);
        assertTrue(adapter.consumeAttempt(attemptId), "Must return true when 1 row updated");

        UUID alreadyConsumedId = UUID.randomUUID();
        when(mockRepo.consumeAttempt(eq(alreadyConsumedId), any(Instant.class))).thenReturn(0);
        assertFalse(adapter.consumeAttempt(alreadyConsumedId), "Must return false when 0 rows updated");
    }

    @Test
    @DisplayName("findActiveAttemptBySessionId queries attempt with status ISSUED")
    void testFindActiveAttemptBySessionId() {
        SpringDataAttemptRepository mockRepo = mock(SpringDataAttemptRepository.class);
        JpaAttemptRepositoryAdapter adapter = new JpaAttemptRepositoryAdapter(mockRepo, new ObjectMapper());

        UUID sessionId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        AttemptEntity entity = new AttemptEntity(
            attemptId, sessionId, "learner-1", "two-pointers", "tp-q1",
            "CONCEPT_ASSESSMENT", "CONCEPT", "EASY", "Evidence expected", "[]",
            0, "ISSUED", Instant.now(), null
        );

        when(mockRepo.findBySessionIdAndStatus(sessionId, "ISSUED")).thenReturn(Optional.of(entity));

        Optional<AttemptRecord> result = adapter.findActiveAttemptBySessionId(sessionId);
        assertTrue(result.isPresent());
        assertEquals(attemptId.toString(), result.get().context().attemptId());
        assertEquals("ISSUED", result.get().status());
    }

    @Test
    @DisplayName("findChronologicalHistory returns mapped domain EvidenceRecord list")
    void testFindChronologicalHistory() {
        SpringDataEvidenceRepository mockRepo = mock(SpringDataEvidenceRepository.class);
        JpaEvidenceRepositoryAdapter adapter = new JpaEvidenceRepositoryAdapter(mockRepo);

        EvidenceEntity entity1 = new EvidenceEntity(
            UUID.randomUUID(), UUID.randomUUID(), "learner-1", "binary-search", "bs-01",
            "CONCEPT", "EASY", 0, false, "PASS", null, "Well done", Instant.now().minusSeconds(10)
        );
        entity1.setEvidenceId(1L);

        EvidenceEntity entity2 = new EvidenceEntity(
            UUID.randomUUID(), UUID.randomUUID(), "learner-1", "binary-search", "bs-02",
            "IMPLEMENTATION", "MEDIUM", 1, false, "PASS", null, "Passed tests", Instant.now()
        );
        entity2.setEvidenceId(2L);

        when(mockRepo.findByLearnerIdAndTopicIdOrderByCreatedAtAscEvidenceIdAsc("learner-1", "binary-search"))
            .thenReturn(List.of(entity1, entity2));

        List<EvidenceRecord> history = adapter.findChronologicalHistory("learner-1", "binary-search");
        assertEquals(2, history.size());
        assertEquals("bs-01", history.get(0).problemId());
        assertEquals("bs-02", history.get(1).problemId());
        assertEquals(EvaluationResult.PASS, history.get(0).result());
    }
}
