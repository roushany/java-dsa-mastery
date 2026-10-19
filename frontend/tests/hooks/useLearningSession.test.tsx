import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { useLearningSession } from '../../src/hooks/useLearningSession';
import { ApiClient } from '../../src/api/client';
import {
  HintResponse,
  QuestionResponse,
  SubmitAnswerResponse,
} from '../../src/api/types';
import { ApiError } from '../../src/api/errors';
import {
  saveSession,
  getStoredSessions,
} from '../../src/storage/sessionIndexStorage';

describe('useLearningSession hook', () => {
  const sessionId = '11111111-2222-3333-4444-555555555555';

  const mockQuestion: QuestionResponse = {
    attemptId: 'attempt-aaaa-bbbb',
    sessionId,
    topicId: 'linear-search',
    stage: 'CONCEPT_ASSESSMENT',
    difficulty: 'EASY',
    content: 'Why does linear search examine elements sequentially?',
    hintsUsed: 0,
    isTerminal: false,
  };

  const mockHint: HintResponse = {
    attemptId: 'attempt-aaaa-bbbb',
    hintLevel: 1,
    hintText: 'Consider unsorted arrays without known order.',
  };

  const mockSubmitResponse: SubmitAnswerResponse = {
    attemptId: 'attempt-aaaa-bbbb',
    evaluation: 'PASS',
    feedback: 'Evaluation completed: PASS',
    misconceptionDetected: null,
    currentState: 'DERIVE',
    currentMastery: 'FAMILIAR',
    stageAdvanced: true,
    hintsUsed: 1,
  };

  beforeEach(() => {
    localStorage.clear();
    saveSession({
      sessionId,
      topicId: 'linear-search',
      createdAt: Date.now(),
      updatedAt: Date.now(),
      lastKnownState: 'CONCEPT_ASSESSMENT',
    });
  });

  it('loads current active question successfully on mount', async () => {
    const mockClient = {
      getCurrentQuestion: vi.fn().mockResolvedValue(mockQuestion),
    } as unknown as ApiClient;

    const { result } = renderHook(() =>
      useLearningSession(sessionId, mockClient)
    );

    expect(result.current.isLoadingQuestion).toBe(true);

    await waitFor(() => {
      expect(result.current.isLoadingQuestion).toBe(false);
    });

    expect(result.current.question).toEqual(mockQuestion);
    expect(result.current.questionError).toBeNull();
    expect(mockClient.getCurrentQuestion).toHaveBeenCalledWith(sessionId);
  });

  it('handles question load error and prunes session if 404 or 410', async () => {
    const notFoundError = new ApiError(404, {
      error: 'SESSION_NOT_FOUND',
      message: 'Session not found in server storage',
      retryable: false,
      timestamp: Date.now(),
    });

    const mockClient = {
      getCurrentQuestion: vi.fn().mockRejectedValue(notFoundError),
    } as unknown as ApiClient;

    const { result } = renderHook(() =>
      useLearningSession(sessionId, mockClient)
    );

    await waitFor(() => {
      expect(result.current.isLoadingQuestion).toBe(false);
    });

    expect(result.current.question).toBeNull();
    expect(result.current.questionError).toBe(notFoundError);
    // Verified: pruned from local storage
    expect(getStoredSessions()).toHaveLength(0);
  });

  it('protects against stale asynchronous responses when sessionId changes', async () => {
    let resolveFirst!: (val: QuestionResponse) => void;
    const slowPromise = new Promise<QuestionResponse>((res) => {
      resolveFirst = res;
    });

    const secondQuestion: QuestionResponse = {
      ...mockQuestion,
      sessionId: '22222222-3333-4444-5555-666666666666',
      content: 'Second session question',
    };

    const mockClient = {
      getCurrentQuestion: vi
        .fn()
        .mockReturnValueOnce(slowPromise)
        .mockResolvedValueOnce(secondQuestion),
    } as unknown as ApiClient;

    const { result, rerender } = renderHook(
      ({ id }) => useLearningSession(id, mockClient),
      {
        initialProps: { id: sessionId },
      }
    );

    // Switch session before first request resolves
    rerender({ id: '22222222-3333-4444-5555-666666666666' });

    await waitFor(() => {
      expect(result.current.question?.content).toBe('Second session question');
    });

    // Now resolve the stale first request
    act(() => {
      resolveFirst(mockQuestion);
    });

    // Should still have second session question, NOT overwritten by stale first request
    expect(result.current.question?.content).toBe('Second session question');
  });

  it('requests progressive hints successfully and preserves question', async () => {
    const mockClient = {
      getCurrentQuestion: vi.fn().mockResolvedValue(mockQuestion),
      requestHint: vi.fn().mockResolvedValue(mockHint),
    } as unknown as ApiClient;

    const { result } = renderHook(() =>
      useLearningSession(sessionId, mockClient)
    );

    await waitFor(() => {
      expect(result.current.isLoadingQuestion).toBe(false);
    });

    await act(async () => {
      await result.current.requestHint();
    });

    expect(mockClient.requestHint).toHaveBeenCalledWith(sessionId, {
      attemptId: 'attempt-aaaa-bbbb',
    });
    expect(result.current.hints).toHaveLength(1);
    expect(result.current.hints[0]).toEqual(mockHint);
    expect(result.current.hintError).toBeNull();
    // Question preserved
    expect(result.current.question).toEqual(mockQuestion);
  });

  it('handles hint request error and sets rate limit countdown if 429', async () => {
    const rateLimitError = new ApiError(
      429,
      {
        error: 'RATE_LIMIT_EXCEEDED',
        message: 'Rate limit reached',
        retryable: true,
        timestamp: Date.now(),
      },
      3
    );

    const mockClient = {
      getCurrentQuestion: vi.fn().mockResolvedValue(mockQuestion),
      requestHint: vi.fn().mockRejectedValue(rateLimitError),
    } as unknown as ApiClient;

    const { result } = renderHook(() =>
      useLearningSession(sessionId, mockClient)
    );

    await waitFor(() => {
      expect(result.current.isLoadingQuestion).toBe(false);
    });

    await act(async () => {
      await result.current.requestHint();
    });

    expect(result.current.hintError).toBe(rateLimitError);
    expect(result.current.rateLimitCountdown).toBe(3);
    // Question still intact
    expect(result.current.question).toEqual(mockQuestion);
  });

  it('submits answer successfully and updates local session pointer', async () => {
    const mockClient = {
      getCurrentQuestion: vi.fn().mockResolvedValue(mockQuestion),
      submitAnswer: vi.fn().mockResolvedValue(mockSubmitResponse),
    } as unknown as ApiClient;

    const { result } = renderHook(() =>
      useLearningSession(sessionId, mockClient)
    );

    await waitFor(() => {
      expect(result.current.isLoadingQuestion).toBe(false);
    });

    let submitResult: SubmitAnswerResponse | null = null;
    await act(async () => {
      submitResult = await result.current.submitAnswer(
        'Because elements have no order guarantee.'
      );
    });

    expect(submitResult).toEqual(mockSubmitResponse);
    expect(result.current.evaluationResult).toEqual(mockSubmitResponse);
    expect(result.current.submitError).toBeNull();

    // Local storage pointer updated with DERIVE state
    const stored = getStoredSessions();
    expect(stored[0].lastKnownState).toBe('DERIVE');
    expect(stored[0].lastKnownMastery).toBe('FAMILIAR');
  });

  it('validates blank and over-length submissions without calling backend', async () => {
    const mockClient = {
      getCurrentQuestion: vi.fn().mockResolvedValue(mockQuestion),
      submitAnswer: vi.fn(),
    } as unknown as ApiClient;

    const { result } = renderHook(() =>
      useLearningSession(sessionId, mockClient)
    );

    await waitFor(() => {
      expect(result.current.isLoadingQuestion).toBe(false);
    });

    // Blank answer test
    await act(async () => {
      await result.current.submitAnswer('   ');
    });

    expect(result.current.submitError?.message).toContain(
      'Answer cannot be blank'
    );
    expect(mockClient.submitAnswer).not.toHaveBeenCalled();

    // Over 2000 chars test
    await act(async () => {
      await result.current.submitAnswer('a'.repeat(2001));
    });

    expect(result.current.submitError?.message).toContain(
      'Answer exceeds maximum allowed length of 2000 characters'
    );
    expect(mockClient.submitAnswer).not.toHaveBeenCalled();
  });

  it('preserves question and entered answer state when submission encounters server error', async () => {
    const serverError = new ApiError(500, {
      error: 'INTERNAL_SERVER_ERROR',
      message: 'AI evaluation failed',
      retryable: true,
      timestamp: Date.now(),
    });

    const mockClient = {
      getCurrentQuestion: vi.fn().mockResolvedValue(mockQuestion),
      submitAnswer: vi.fn().mockRejectedValue(serverError),
    } as unknown as ApiClient;

    const { result } = renderHook(() =>
      useLearningSession(sessionId, mockClient)
    );

    await waitFor(() => {
      expect(result.current.isLoadingQuestion).toBe(false);
    });

    await act(async () => {
      await result.current.submitAnswer('My candidate explanation');
    });

    expect(result.current.submitError).toBe(serverError);
    expect(result.current.question).toEqual(mockQuestion);
  });

  it('advances to next question resetting evaluation and hints', async () => {
    const nextQuestion: QuestionResponse = {
      ...mockQuestion,
      stage: 'DERIVE',
      content: 'Derive the index formula.',
    };

    const mockClient = {
      getCurrentQuestion: vi
        .fn()
        .mockResolvedValueOnce(mockQuestion)
        .mockResolvedValueOnce(nextQuestion),
      submitAnswer: vi.fn().mockResolvedValue(mockSubmitResponse),
    } as unknown as ApiClient;

    const { result } = renderHook(() =>
      useLearningSession(sessionId, mockClient)
    );

    await waitFor(() => {
      expect(result.current.isLoadingQuestion).toBe(false);
    });

    await act(async () => {
      await result.current.submitAnswer('Correct explanation');
    });

    expect(result.current.evaluationResult).not.toBeNull();

    // Advance
    await act(async () => {
      await result.current.advanceToNextQuestion();
    });

    expect(result.current.evaluationResult).toBeNull();
    expect(result.current.hints).toHaveLength(0);
    expect(result.current.question?.stage).toBe('DERIVE');
    expect(result.current.question?.content).toBe('Derive the index formula.');
  });

  it('rejects stale hint responses when the user moves to a new question before hint resolves', async () => {
    let resolveHint!: (val: HintResponse) => void;
    const delayedHintPromise = new Promise<HintResponse>((res) => {
      resolveHint = res;
    });

    const questionA: QuestionResponse = {
      attemptId: 'attempt-aaaa-1111',
      sessionId,
      topicId: 'linear-search',
      stage: 'CONCEPT_ASSESSMENT',
      difficulty: 'EASY',
      content: 'Question A prompt',
      hintsUsed: 0,
      isTerminal: false,
    };

    const questionB: QuestionResponse = {
      attemptId: 'attempt-bbbb-2222',
      sessionId,
      topicId: 'linear-search',
      stage: 'DERIVE',
      difficulty: 'MEDIUM',
      content: 'Question B prompt',
      hintsUsed: 0,
      isTerminal: false,
    };

    const staleHintForA: HintResponse = {
      attemptId: 'attempt-aaaa-1111',
      hintLevel: 1,
      hintText: 'Stale hint text for Question A',
    };

    const validHintForB: HintResponse = {
      attemptId: 'attempt-bbbb-2222',
      hintLevel: 1,
      hintText: 'Valid hint text for Question B',
    };

    const mockClient = {
      getCurrentQuestion: vi
        .fn()
        .mockResolvedValueOnce(questionA)
        .mockResolvedValueOnce(questionB),
      requestHint: vi
        .fn()
        .mockReturnValueOnce(delayedHintPromise)
        .mockResolvedValueOnce(validHintForB),
    } as unknown as ApiClient;

    const { result } = renderHook(() =>
      useLearningSession(sessionId, mockClient)
    );

    await waitFor(() => {
      expect(result.current.isLoadingQuestion).toBe(false);
      expect(result.current.question?.attemptId).toBe('attempt-aaaa-1111');
    });

    // Request hint for Question A (in flight)
    let hintRequestPromise: Promise<void> | undefined;
    act(() => {
      hintRequestPromise = result.current.requestHint();
    });
    expect(result.current.isRequestingHint).toBe(true);

    // User advances to Question B before Question A's hint resolves
    await act(async () => {
      await result.current.advanceToNextQuestion();
    });

    expect(result.current.question?.attemptId).toBe('attempt-bbbb-2222');
    expect(result.current.hints).toHaveLength(0);

    // Now resolve the late-arriving hint for Question A
    await act(async () => {
      resolveHint(staleHintForA);
      await hintRequestPromise;
    });

    // Crucial check: Question A's late hint must NOT be appended to Question B!
    expect(result.current.question?.attemptId).toBe('attempt-bbbb-2222');
    expect(result.current.hints).toHaveLength(0);
    expect(result.current.isRequestingHint).toBe(false);

    // A subsequent hint request for Question B should succeed and append properly
    await act(async () => {
      await result.current.requestHint();
    });

    expect(result.current.hints).toHaveLength(1);
    expect(result.current.hints[0]).toEqual(validHintForB);
  });
});
