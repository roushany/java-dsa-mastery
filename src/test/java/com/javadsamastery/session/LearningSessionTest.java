package com.javadsamastery.session;

import com.javadsamastery.ai.mock.MockLLMProvider;
import com.javadsamastery.ai.service.impl.LLMAnswerEvaluator;
import com.javadsamastery.ai.service.impl.LLMQuestionGenerator;
import com.javadsamastery.ai.service.impl.LLMTutor;
import com.javadsamastery.domain.learner.LearnerProfile;
import com.javadsamastery.engine.AITutorEngine;

import com.javadsamastery.engine.MasteryEngine;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import com.javadsamastery.engine.QuestionSelector;
import com.javadsamastery.domain.content.QuestionBank;
import com.javadsamastery.domain.content.QuestionDefinition;
import com.javadsamastery.domain.lesson.LearningState;
import com.javadsamastery.domain.problem.ProblemDifficulty;
import java.io.ByteArrayInputStream;

class LearningSessionTest {

    @Test
    void testSessionLifecycle() {
        LearnerProfile profile = new LearnerProfile("u1");
        MasteryEngine masteryEngine = new MasteryEngine();
        
        QuestionBank bank = new QuestionBank();
        String json = """
            [
              {
                "questionId": "q1",
                "topicId": "arrays",
                "targetStage": "CONCEPT_ASSESSMENT",
                "difficulty": "MEDIUM",
                "content": "What is an array?",
                "expectedEvidence": "Contiguous memory",
                "targetsMisconception": null,
                "isRemediation": false,
                "isTransfer": false,
                "isRetrieval": false
              }
            ]
            """;
        bank.loadFromJsonStream(new ByteArrayInputStream(json.getBytes()));
        QuestionSelector selector = new QuestionSelector(bank);
        
        MockLLMProvider provider = new MockLLMProvider();
        AITutorEngine tutor = new AITutorEngine(
            new LLMTutor(provider), 
            new LLMQuestionGenerator(provider), 
            new LLMAnswerEvaluator(provider)
        );
        
        LearningSession session = new LearningSession(profile, masteryEngine, selector, tutor);

        assertFalse(session.isActive());
        session.start();
        assertTrue(session.isActive());
        
        // Orchestration integration test
        var q = session.getNextQuestion("arrays", LearningState.CONCEPT_ASSESSMENT);
        assertNotNull(q);
        
        var evidence = session.submitAnswer(q.expectedConcept(), "answer", 0, true);
        assertNotNull(evidence);
        assertEquals(1, session.getSessionHistory().size());
        
        session.end();
        assertFalse(session.isActive());
        assertEquals(profile, session.getLearnerProfile());
    }
}
