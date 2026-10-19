import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LearningWorkspace } from '../../src/features/learning/LearningWorkspace';
import { ApiClient } from '../../src/api/client';
import {
  HintResponse,
  QuestionResponse,
  SessionResponse,
  SubmitAnswerResponse,
} from '../../src/api/types';
import { ApiError } from '../../src/api/errors';

describe('LearningWorkspace Component', () => {
  const baseSession: SessionResponse = {
    sessionId: '11111111-2222-3333-4444-555555555555',
    learnerId: 'learner-alice',
    topicId: 'linear-search',
    topicTitle: 'Linear Search',
    currentState: 'CONCEPT_ASSESSMENT',
    mastery: 'NOVICE',
    totalStages: 1,
    completed: false,
  };

  const conceptualQuestion: QuestionResponse = {
    attemptId: 'attempt-c111-2222',
    sessionId: baseSession.sessionId,
    topicId: 'linear-search',
    stage: 'CONCEPT_ASSESSMENT',
    difficulty: 'EASY',
    content:
      'Why is it impossible to eliminate half of an unsorted array like we do in Binary Search?',
    hintsUsed: 0,
    isTerminal: false,
  };

  const implementationQuestion: QuestionResponse = {
    attemptId: 'attempt-i333-4444',
    sessionId: baseSession.sessionId,
    topicId: 'linear-search',
    stage: 'IMPLEMENTATION',
    difficulty: 'MEDIUM',
    content: 'Write a Java method to perform a linear search returning the index.',
    hintsUsed: 0,
    isTerminal: false,
  };

  const sampleHint: HintResponse = {
    attemptId: 'attempt-c111-2222',
    hintLevel: 1,
    hintText: 'Consider what properties are guaranteed in unsorted data.',
  };

  const passSubmitResponse: SubmitAnswerResponse = {
    attemptId: 'attempt-c111-2222',
    evaluation: 'PASS',
    feedback: 'Evaluation completed: PASS. Great derivation of the requirement.',
    misconceptionDetected: null,
    currentState: 'DERIVE',
    currentMastery: 'FAMILIAR',
    stageAdvanced: true,
    hintsUsed: 0,
  };

  const failSubmitResponse: SubmitAnswerResponse = {
    attemptId: 'attempt-c111-2222',
    evaluation: 'FAIL',
    feedback: 'Evaluation completed: FAIL. Missing core ordering requirement.',
    misconceptionDetected: 'loop-syntax',
    currentState: 'CONCEPT_ASSESSMENT',
    currentMastery: 'NOVICE',
    stageAdvanced: false,
    hintsUsed: 1,
  };

  beforeEach(() => {
    localStorage.clear();
  });

  it('renders loading spinner while question is being loaded', () => {
    const mockClient = {
      getCurrentQuestion: vi.fn(() => new Promise(() => {})), // Never resolves
    } as unknown as ApiClient;

    render(
      <LearningWorkspace
        session={baseSession}
        client={mockClient}
        onNavigateToCurriculum={vi.fn()}
        onNavigateToDashboard={vi.fn()}
      />
    );

    expect(screen.getByTestId('workspace-loading')).toBeInTheDocument();
    expect(
      screen.getByText('Loading active pedagogical question...')
    ).toBeInTheDocument();
  });

  it('renders error state and retries question loading when Retry button is clicked', async () => {
    const user = userEvent.setup();
    const mockClient = {
      getCurrentQuestion: vi
        .fn()
        .mockRejectedValueOnce(
          new ApiError(500, {
            error: 'DATABASE_TIMEOUT',
            message: 'Question bank query timed out',
            retryable: true,
            timestamp: Date.now(),
          })
        )
        .mockResolvedValueOnce(conceptualQuestion),
    } as unknown as ApiClient;

    render(
      <LearningWorkspace
        session={baseSession}
        client={mockClient}
        onNavigateToCurriculum={vi.fn()}
        onNavigateToDashboard={vi.fn()}
      />
    );

    await waitFor(() => {
      expect(screen.getByTestId('question-error')).toBeInTheDocument();
    });

    expect(screen.getByText('Question bank query timed out')).toBeInTheDocument();

    const retryBtn = screen.getByRole('button', { name: 'Retry Loading' });
    await user.click(retryBtn);

    await waitFor(() => {
      expect(screen.getByTestId('active-workspace')).toBeInTheDocument();
    });

    expect(
      screen.getByText(conceptualQuestion.content)
    ).toBeInTheDocument();
  });

  it('renders completed celebration screen for terminal COMPLETED session', async () => {
    const user = userEvent.setup();
    const onNavigateToCurriculum = vi.fn();

    const terminalQuestion: QuestionResponse = {
      attemptId: null,
      sessionId: baseSession.sessionId,
      topicId: 'linear-search',
      stage: 'COMPLETED',
      difficulty: null,
      content: 'Lesson terminated: COMPLETED',
      hintsUsed: 0,
      isTerminal: true,
    };

    const mockClient = {
      getCurrentQuestion: vi.fn().mockResolvedValue(terminalQuestion),
    } as unknown as ApiClient;

    render(
      <LearningWorkspace
        session={baseSession}
        client={mockClient}
        onNavigateToCurriculum={onNavigateToCurriculum}
        onNavigateToDashboard={vi.fn()}
      />
    );

    await waitFor(() => {
      expect(screen.getByTestId('terminal-session')).toBeInTheDocument();
    });

    expect(screen.getByText('Topic Completed!')).toBeInTheDocument();
    expect(screen.getAllByText(/Linear Search/).length).toBeGreaterThan(0);

    const browseBtn = screen.getByRole('button', { name: 'Browse Curriculum' });
    await user.click(browseBtn);
    expect(onNavigateToCurriculum).toHaveBeenCalledTimes(1);
  });

  it('renders loop protection screen for terminal FAILED_TERMINATED session', async () => {
    const terminalQuestion: QuestionResponse = {
      attemptId: null,
      sessionId: baseSession.sessionId,
      topicId: 'linear-search',
      stage: 'FAILED_TERMINATED',
      difficulty: null,
      content: 'Lesson terminated: FAILED_TERMINATED',
      hintsUsed: 0,
      isTerminal: true,
    };

    const mockClient = {
      getCurrentQuestion: vi.fn().mockResolvedValue(terminalQuestion),
    } as unknown as ApiClient;

    render(
      <LearningWorkspace
        session={baseSession}
        client={mockClient}
        onNavigateToCurriculum={vi.fn()}
        onNavigateToDashboard={vi.fn()}
      />
    );

    await waitFor(() => {
      expect(screen.getByTestId('terminal-session')).toBeInTheDocument();
    });

    expect(
      screen.getByText('Session Paused: Maximum Attempts Reached')
    ).toBeInTheDocument();
  });

  it('renders conceptual question with standard explanation input and no code notice', async () => {
    const mockClient = {
      getCurrentQuestion: vi.fn().mockResolvedValue(conceptualQuestion),
    } as unknown as ApiClient;

    render(
      <LearningWorkspace
        session={baseSession}
        client={mockClient}
        onNavigateToCurriculum={vi.fn()}
        onNavigateToDashboard={vi.fn()}
      />
    );

    await waitFor(() => {
      expect(screen.getByTestId('active-workspace')).toBeInTheDocument();
    });

    expect(screen.getByText(conceptualQuestion.content)).toBeInTheDocument();
    expect(screen.getByText('Stage: Concept Assessment')).toBeInTheDocument();
    expect(screen.getByText('EASY')).toBeInTheDocument();
    expect(screen.getByText('Your Explanation')).toBeInTheDocument();

    // Code honesty notice must NOT appear for conceptual questions
    expect(screen.queryByTestId('code-honesty-notice')).not.toBeInTheDocument();
  });

  it('renders implementation question with code disclaimer and code editor styling', async () => {
    const mockClient = {
      getCurrentQuestion: vi.fn().mockResolvedValue(implementationQuestion),
    } as unknown as ApiClient;

    render(
      <LearningWorkspace
        session={{ ...baseSession, currentState: 'IMPLEMENTATION' }}
        client={mockClient}
        onNavigateToCurriculum={vi.fn()}
        onNavigateToDashboard={vi.fn()}
      />
    );

    await waitFor(() => {
      expect(screen.getByTestId('active-workspace')).toBeInTheDocument();
    });

    expect(
      screen.getByText(implementationQuestion.content)
    ).toBeInTheDocument();
    expect(screen.getByText('Stage: Implementation')).toBeInTheDocument();
    expect(screen.getByText('MEDIUM')).toBeInTheDocument();
    expect(screen.getByText('Java Method Implementation')).toBeInTheDocument();

    // Code honesty notice MUST be prominently rendered with honest limitations
    expect(screen.getByTestId('code-honesty-notice')).toBeInTheDocument();
    expect(
      screen.getByText(/Submitted Java code is not compiled or executed in an isolated sandbox/)
    ).toBeInTheDocument();
    expect(
      screen.getByText(/automated unit-test verification is currently unavailable/)
    ).toBeInTheDocument();
    expect(
      screen.getByText(/transition to the debugging stage for pedagogical review rather than receiving a verified code pass/)
    ).toBeInTheDocument();
  });

  it('updates character count and caps input at 2000 characters', async () => {
    const mockClient = {
      getCurrentQuestion: vi.fn().mockResolvedValue(conceptualQuestion),
    } as unknown as ApiClient;

    render(
      <LearningWorkspace
        session={baseSession}
        client={mockClient}
        onNavigateToCurriculum={vi.fn()}
        onNavigateToDashboard={vi.fn()}
      />
    );

    await waitFor(() => {
      expect(screen.getByTestId('char-counter')).toBeInTheDocument();
    });

    expect(screen.getByTestId('char-counter')).toHaveTextContent('0 / 2000');

    const textarea = screen.getByRole('textbox', { name: /Your Explanation/i });
    fireEvent.change(textarea, { target: { value: 'Hello World' } });

    expect(screen.getByTestId('char-counter')).toHaveTextContent('11 / 2000');

    // Attempt typing beyond 2000 characters
    const longText = 'a'.repeat(2500);
    fireEvent.change(textarea, { target: { value: longText } });

    // Should not accept length > 2000
    expect((textarea as HTMLTextAreaElement).value.length).toBe(11);
  });

  it('submits answer successfully, displays PASS evaluation, and advances to next stage', async () => {
    const user = userEvent.setup();
    const nextQuestion: QuestionResponse = {
      attemptId: 'attempt-next-5555',
      sessionId: baseSession.sessionId,
      topicId: 'linear-search',
      stage: 'DERIVE',
      difficulty: 'MEDIUM',
      content: 'Derive the sequential scan loop invariant.',
      hintsUsed: 0,
      isTerminal: false,
    };

    const mockClient = {
      getCurrentQuestion: vi
        .fn()
        .mockResolvedValueOnce(conceptualQuestion)
        .mockResolvedValueOnce(nextQuestion),
      submitAnswer: vi.fn().mockResolvedValue(passSubmitResponse),
    } as unknown as ApiClient;

    render(
      <LearningWorkspace
        session={baseSession}
        client={mockClient}
        onNavigateToCurriculum={vi.fn()}
        onNavigateToDashboard={vi.fn()}
      />
    );

    await waitFor(() => {
      expect(screen.getByTestId('active-workspace')).toBeInTheDocument();
    });

    const submitBtn = screen.getByTestId('btn-submit-answer');
    expect(submitBtn).toBeDisabled();

    // Type answer
    const textarea = screen.getByRole('textbox', { name: /Your Explanation/i });
    fireEvent.change(textarea, {
      target: { value: 'In an unsorted array, no element order is known.' },
    });

    expect(submitBtn).not.toBeDisabled();
    await user.click(submitBtn);

    expect(mockClient.submitAnswer).toHaveBeenCalledTimes(1);
    expect(mockClient.submitAnswer).toHaveBeenCalledWith(baseSession.sessionId, {
      attemptId: conceptualQuestion.attemptId,
      answer: 'In an unsorted array, no element order is known.',
    });

    // Evaluation feedback displayed
    await waitFor(() => {
      expect(screen.getByTestId('evaluation-feedback')).toBeInTheDocument();
    });

    expect(screen.getByText('Concept Verified (PASS)')).toBeInTheDocument();
    expect(
      screen.getByText('Stage Advanced → DERIVE')
    ).toBeInTheDocument();

    // Click Continue to Next Stage
    const nextStageBtn = screen.getByTestId('btn-next-question');
    await user.click(nextStageBtn);

    await waitFor(() => {
      expect(screen.getByText(nextQuestion.content)).toBeInTheDocument();
    });

    expect(screen.getByText('Stage: Derive')).toBeInTheDocument();
  });

  it('displays FAIL evaluation with detected misconception badge', async () => {
    const user = userEvent.setup();
    const mockClient = {
      getCurrentQuestion: vi.fn().mockResolvedValue(conceptualQuestion),
      submitAnswer: vi.fn().mockResolvedValue(failSubmitResponse),
    } as unknown as ApiClient;

    render(
      <LearningWorkspace
        session={baseSession}
        client={mockClient}
        onNavigateToCurriculum={vi.fn()}
        onNavigateToDashboard={vi.fn()}
      />
    );

    await waitFor(() => {
      expect(screen.getByTestId('active-workspace')).toBeInTheDocument();
    });

    const textarea = screen.getByRole('textbox', { name: /Your Explanation/i });
    fireEvent.change(textarea, { target: { value: 'Incomplete explanation' } });

    const submitBtn = screen.getByTestId('btn-submit-answer');
    await user.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByTestId('evaluation-feedback')).toBeInTheDocument();
    });

    expect(screen.getByText('Evaluation Result: FAIL')).toBeInTheDocument();
    expect(screen.getByText('loop-syntax')).toBeInTheDocument();
    expect(screen.getByTestId('btn-next-question')).toHaveTextContent(
      'Try Next Question'
    );
  });

  it('supports keyboard shortcut Ctrl+Enter to submit answer', async () => {
    const mockClient = {
      getCurrentQuestion: vi.fn().mockResolvedValue(conceptualQuestion),
      submitAnswer: vi.fn().mockResolvedValue(passSubmitResponse),
    } as unknown as ApiClient;

    render(
      <LearningWorkspace
        session={baseSession}
        client={mockClient}
        onNavigateToCurriculum={vi.fn()}
        onNavigateToDashboard={vi.fn()}
      />
    );

    await waitFor(() => {
      expect(screen.getByTestId('active-workspace')).toBeInTheDocument();
    });

    const textarea = screen.getByRole('textbox', { name: /Your Explanation/i });
    fireEvent.change(textarea, { target: { value: 'Valid shortcut explanation' } });

    // Press Ctrl+Enter
    fireEvent.keyDown(textarea, { key: 'Enter', ctrlKey: true });

    await waitFor(() => {
      expect(mockClient.submitAnswer).toHaveBeenCalledTimes(1);
    });
  });

  it('requests progressive hints, displays hint list, and handles hint errors cleanly', async () => {
    const user = userEvent.setup();
    const mockClient = {
      getCurrentQuestion: vi.fn().mockResolvedValue(conceptualQuestion),
      requestHint: vi
        .fn()
        .mockResolvedValueOnce(sampleHint)
        .mockRejectedValueOnce(
          new ApiError(500, {
            error: 'AI_TIMEOUT',
            message: 'Hint generation service timed out',
            retryable: true,
            timestamp: Date.now(),
          })
        ),
    } as unknown as ApiClient;

    render(
      <LearningWorkspace
        session={baseSession}
        client={mockClient}
        onNavigateToCurriculum={vi.fn()}
        onNavigateToDashboard={vi.fn()}
      />
    );

    await waitFor(() => {
      expect(screen.getByTestId('active-workspace')).toBeInTheDocument();
    });

    expect(screen.getByText('0 revealed')).toBeInTheDocument();

    const requestHintBtn = screen.getByTestId('btn-request-hint');
    expect(requestHintBtn).toHaveTextContent('Request Hint 1');

    await user.click(requestHintBtn);

    await waitFor(() => {
      expect(screen.getByTestId('hints-list')).toBeInTheDocument();
    });

    expect(screen.getByText('Hint Level 1:')).toBeInTheDocument();
    expect(screen.getByText(sampleHint.hintText)).toBeInTheDocument();
    expect(screen.getByText('1 revealed')).toBeInTheDocument();

    // Request second hint (which fails)
    expect(requestHintBtn).toHaveTextContent('Request Hint 2');
    await user.click(requestHintBtn);

    await waitFor(() => {
      expect(screen.getByTestId('hint-error')).toBeInTheDocument();
    });

    expect(
      screen.getByText('Hint generation service timed out')
    ).toBeInTheDocument();

    // Verify question prompt and first hint are still intact!
    expect(screen.getByText(conceptualQuestion.content)).toBeInTheDocument();
    expect(screen.getByText(sampleHint.hintText)).toBeInTheDocument();
  });

  it('handles rate limiting (429) on submit with retry-after countdown', async () => {
    const user = userEvent.setup();
    const rateLimitError = new ApiError(
      429,
      {
        error: 'RATE_LIMIT_EXCEEDED',
        message: 'Evaluation rate limit exceeded. Please wait.',
        retryable: true,
        timestamp: Date.now(),
      },
      5
    );

    const mockClient = {
      getCurrentQuestion: vi.fn().mockResolvedValue(conceptualQuestion),
      submitAnswer: vi.fn().mockRejectedValue(rateLimitError),
    } as unknown as ApiClient;

    render(
      <LearningWorkspace
        session={baseSession}
        client={mockClient}
        onNavigateToCurriculum={vi.fn()}
        onNavigateToDashboard={vi.fn()}
      />
    );

    await waitFor(() => {
      expect(screen.getByTestId('active-workspace')).toBeInTheDocument();
    });

    const textarea = screen.getByRole('textbox', { name: /Your Explanation/i });
    fireEvent.change(textarea, { target: { value: 'Rate limited text' } });

    const submitBtn = screen.getByTestId('btn-submit-answer');
    await user.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByTestId('submit-error')).toBeInTheDocument();
    });

    expect(
      screen.getByText('Evaluation rate limit exceeded. Please wait.')
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Rate limited: please wait 5s before retrying\./)
    ).toBeInTheDocument();

    // Submit button should be disabled with countdown
    expect(submitBtn).toBeDisabled();
    expect(submitBtn).toHaveTextContent('Wait 5s...');
  });

  it('triggers navigation callbacks for curriculum and dashboard links', async () => {
    const user = userEvent.setup();
    const onNavigateToCurriculum = vi.fn();
    const onNavigateToDashboard = vi.fn();

    const mockClient = {
      getCurrentQuestion: vi.fn().mockResolvedValue(conceptualQuestion),
    } as unknown as ApiClient;

    render(
      <LearningWorkspace
        session={baseSession}
        client={mockClient}
        onNavigateToCurriculum={onNavigateToCurriculum}
        onNavigateToDashboard={onNavigateToDashboard}
      />
    );

    await waitFor(() => {
      expect(screen.getByTestId('active-workspace')).toBeInTheDocument();
    });

    const curriculumLink = screen.getByRole('button', { name: '← Curriculum' });
    await user.click(curriculumLink);
    expect(onNavigateToCurriculum).toHaveBeenCalledTimes(1);

    const dashboardLink = screen.getByRole('button', { name: 'Dashboard' });
    await user.click(dashboardLink);
    expect(onNavigateToDashboard).toHaveBeenCalledTimes(1);
  });

  it('displays session expired recovery alert and returns to dashboard without creating replacement session on 410', async () => {
    const user = userEvent.setup();
    const onNavigateToDashboard = vi.fn();
    const expiredError = new ApiError(410, {
      error: 'SESSION_EXPIRED',
      message: 'Session has expired or timed out',
      retryable: false,
      timestamp: Date.now(),
    });

    const mockClient = {
      getCurrentQuestion: vi.fn().mockResolvedValue(conceptualQuestion),
      submitAnswer: vi.fn().mockRejectedValue(expiredError),
      startSession: vi.fn(), // Must NOT be called
    } as unknown as ApiClient;

    render(
      <LearningWorkspace
        session={baseSession}
        client={mockClient}
        onNavigateToCurriculum={vi.fn()}
        onNavigateToDashboard={onNavigateToDashboard}
      />
    );

    await waitFor(() => {
      expect(screen.getByTestId('active-workspace')).toBeInTheDocument();
    });

    const textarea = screen.getByRole('textbox', { name: /Your Explanation/i });
    fireEvent.change(textarea, { target: { value: 'Valid explanation' } });

    const submitBtn = screen.getByTestId('btn-submit-answer');
    await user.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByTestId('submit-error')).toBeInTheDocument();
    });

    // Verify clear expired message
    expect(screen.getByText('Session Expired')).toBeInTheDocument();
    expect(
      screen.getByText(
        /Your learning session has expired or is no longer available on the server\. Please return to the dashboard to select a topic and begin a new session\./
      )
    ).toBeInTheDocument();

    // Verify textarea and submit button are disabled
    expect(textarea).toBeDisabled();
    expect(submitBtn).toBeDisabled();

    // Verify accessible Return to Dashboard action
    const returnBtn = screen.getByTestId('btn-expired-return-dashboard');
    expect(returnBtn).toBeInTheDocument();
    await user.click(returnBtn);

    expect(onNavigateToDashboard).toHaveBeenCalledTimes(1);
    // Crucial: No automatic replacement session was created
    expect(mockClient.startSession).not.toHaveBeenCalled();
  });
});
