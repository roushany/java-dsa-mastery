package com.javadsamastery.infrastructure.persistence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javadsamastery.domain.ports.TopicMasteryRepositoryPort.MasterySnapshotRecord;
import com.javadsamastery.infrastructure.persistence.entity.TopicMasterySnapshotEntity;
import com.javadsamastery.infrastructure.persistence.entity.TopicMasterySnapshotId;
import com.javadsamastery.infrastructure.persistence.repository.SpringDataTopicMasteryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JpaTopicMasteryRepositoryAdapterTest {

    private SpringDataTopicMasteryRepository mockRepository;
    private JpaTopicMasteryRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        mockRepository = mock(SpringDataTopicMasteryRepository.class);
        adapter = new JpaTopicMasteryRepositoryAdapter(mockRepository, new ObjectMapper());
    }

    @Test
    @DisplayName("lockOrInitialize ensures row exists via insertInitialIfAbsent and returns locked row")
    void testLockOrInitialize_InsertsIfAbsentAndLocks() {
        TopicMasterySnapshotEntity entity = new TopicMasterySnapshotEntity(
            "learner-2", "binary-search", "NOVICE", 0, 0, 0, 0, 0, "[]", Instant.now(), 0L
        );
        when(mockRepository.insertInitialIfAbsent("learner-2", "binary-search")).thenReturn(1);
        when(mockRepository.findByLearnerIdAndTopicIdForUpdate("learner-2", "binary-search"))
            .thenReturn(Optional.of(entity));

        MasterySnapshotRecord result = adapter.lockOrInitialize("learner-2", "binary-search");

        assertNotNull(result);
        assertEquals("learner-2", result.learnerId());
        assertEquals("binary-search", result.topicId());
        assertEquals("NOVICE", result.masteryLevel());
        verify(mockRepository, times(1)).insertInitialIfAbsent("learner-2", "binary-search");
        verify(mockRepository, times(1)).findByLearnerIdAndTopicIdForUpdate("learner-2", "binary-search");
    }

    @Test
    @DisplayName("deleteByLearnerIdAndTopicId delegates to Spring Data repository with composite ID")
    void testDeleteByLearnerIdAndTopicId() {
        adapter.deleteByLearnerIdAndTopicId("learner-1", "hashing");
        verify(mockRepository).deleteById(eq(new TopicMasterySnapshotId("learner-1", "hashing")));
    }
}
