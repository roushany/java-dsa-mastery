package com.javadsamastery.domain.evidence;

import com.javadsamastery.domain.problem.ProblemDifficulty;
import com.javadsamastery.domain.lesson.QuestionType;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class EvidenceRecordTest {
    @Test
    void testValidEvidenceRecord() {
        EvidenceRecord record = new EvidenceRecord("l1", "arrays", "p1", ProblemDifficulty.EASY, QuestionType.IMPLEMENTATION, 0, false, EvaluationResult.PASS, null, null);
        assertNotNull(record.timestamp());
        assertEquals("l1", record.learnerId());
    }

    @Test
    void testInvalidEvidenceRecord() {
        assertThrows(IllegalArgumentException.class, () -> 
            new EvidenceRecord("", "arrays", "p1", ProblemDifficulty.EASY, QuestionType.IMPLEMENTATION, 0, false, EvaluationResult.PASS, null, Instant.now()));
            
        assertThrows(IllegalArgumentException.class, () -> 
            new EvidenceRecord("l1", "arrays", "p1", ProblemDifficulty.EASY, QuestionType.IMPLEMENTATION, -1, false, EvaluationResult.PASS, null, Instant.now()));
    }
}
