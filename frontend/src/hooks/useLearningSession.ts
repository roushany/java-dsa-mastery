import { useState, useEffect, useCallback, useRef } from 'react';
import { ApiClient, defaultApiClient } from '../api/client';
import { ApiError } from '../api/errors';
import {
  HintResponse,
  QuestionResponse,
  SubmitAnswerResponse,
} from '../api/types';
import { saveSession, removeSession } from '../storage/sessionIndexStorage';

export interface UseLearningSessionResult {
  question: QuestionResponse | null;
  isLoadingQuestion: boolean;
  questionError: ApiError | null;
  hints: HintResponse[];
  isRequestingHint: boolean;
  hintError: ApiError | null;
  isSubmittingAnswer: boolean;
  submitError: ApiError | null;
  evaluationResult: SubmitAnswerResponse | null;
  rateLimitCountdown: number | null;
  loadCurrentQuestion: () => Promise<void>;
  requestHint: () => Promise<void>;
  submitAnswer: (answer: string) => Promise<SubmitAnswerResponse | null>;
  advanceToNextQuestion: () => Promise<void>;
  dismissErrors: () => void;
}

/**
 * Custom hook to manage the full interactive learning session lifecycle:
 * - Current question loading and refresh
 * - Progressive hint requests with rate limit tracking
 * - Answer submission and evaluation handling
 * - Stale response protection across session transitions
 * - Local session pointer updates on state advancement
 */
export function useLearningSession(
  sessionId: string,
  client: ApiClient = defaultApiClient
): UseLearningSessionResult {
  const [question, setQuestion] = useState<QuestionResponse | null>(null);
  const [isLoadingQuestion, setIsLoadingQuestion] = useState<boolean>(true);
  const [questionError, setQuestionError] = useState<ApiError | null>(null);

  const [hints, setHints] = useState<HintResponse[]>([]);
  const [isRequestingHint, setIsRequestingHint] = useState<boolean>(false);
  const [hintError, setHintError] = useState<ApiError | null>(null);

  const [isSubmittingAnswer, setIsSubmittingAnswer] = useState<boolean>(false);
  const [submitError, setSubmitError] = useState<ApiError | null>(null);
  const [evaluationResult, setEvaluationResult] = useState<SubmitAnswerResponse | null>(null);

  const [rateLimitCountdown, setRateLimitCountdown] = useState<number | null>(null);

  // Stale request protection
  const requestSeqRef = useRef<number>(0);
  const currentSessionIdRef = useRef<string>(sessionId);
  const activeAttemptIdRef = useRef<string | null>(question?.attemptId ?? null);

  // Keep track of active session ID changes
  useEffect(() => {
    currentSessionIdRef.current = sessionId;
  }, [sessionId]);

  // Keep track of active question attempt ID changes
  useEffect(() => {
    activeAttemptIdRef.current = question?.attemptId ?? null;
  }, [question?.attemptId]);

  // Handle rate limit countdown timer
  useEffect(() => {
    if (rateLimitCountdown === null || rateLimitCountdown <= 0) {
      return;
    }
    const timer = setInterval(() => {
      setRateLimitCountdown((prev) => {
        if (prev === null || prev <= 1) {
          return null;
        }
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(timer);
  }, [rateLimitCountdown]);

  /**
   * Loads the current active question for the session.
   * Protects against stale async responses.
   */
  const loadCurrentQuestion = useCallback(async () => {
    if (!sessionId) {
      return;
    }

    const seq = ++requestSeqRef.current;
    setIsLoadingQuestion(true);
    setQuestionError(null);

    try {
      const resp = await client.getCurrentQuestion(sessionId);
      // Guard against stale response if session changed or newer request initiated
      if (seq !== requestSeqRef.current || currentSessionIdRef.current !== sessionId) {
        return;
      }

      setQuestion(resp);
      activeAttemptIdRef.current = resp.attemptId;
      // If question has hints already used recorded by server, keep hint list synced
      setQuestionError(null);
    } catch (err: unknown) {
      if (seq !== requestSeqRef.current || currentSessionIdRef.current !== sessionId) {
        return;
      }

      const apiErr = err instanceof ApiError ? err : ApiError.networkError(err instanceof Error ? err : undefined);
      setQuestionError(apiErr);

      // Prune session if server confirms it is expired or purged
      if (apiErr.isNotFound || apiErr.isSessionExpired) {
        removeSession(sessionId);
      }
    } finally {
      if (seq === requestSeqRef.current && currentSessionIdRef.current === sessionId) {
        setIsLoadingQuestion(false);
      }
    }
  }, [client, sessionId]);

  // Initial question load when sessionId changes
  useEffect(() => {
    setQuestion(null);
    activeAttemptIdRef.current = null;
    setHints([]);
    setEvaluationResult(null);
    setSubmitError(null);
    setHintError(null);
    setQuestionError(null);
    loadCurrentQuestion();
  }, [sessionId, loadCurrentQuestion]);

  /**
   * Requests a progressive hint for the active question attempt.
   */
  const requestHint = useCallback(async () => {
    if (!question || !question.attemptId || isRequestingHint) {
      return;
    }

    const targetAttemptId = question.attemptId;
    setIsRequestingHint(true);
    setHintError(null);

    try {
      const hint = await client.requestHint(sessionId, {
        attemptId: targetAttemptId,
      });

      // Verify hint belongs to active session and currently displayed question attempt
      if (
        currentSessionIdRef.current === sessionId &&
        activeAttemptIdRef.current === targetAttemptId &&
        hint.attemptId === targetAttemptId
      ) {
        setHints((prev) => [...prev, hint]);
      }
    } catch (err: unknown) {
      // Discard errors if user already transitioned away from this question or session
      if (
        currentSessionIdRef.current !== sessionId ||
        activeAttemptIdRef.current !== targetAttemptId
      ) {
        return;
      }

      const apiErr = err instanceof ApiError ? err : ApiError.networkError(err instanceof Error ? err : undefined);
      setHintError(apiErr);

      if (apiErr.isRateLimited) {
        setRateLimitCountdown(apiErr.retryAfterSeconds || 5);
      } else if (apiErr.isNotFound || apiErr.isSessionExpired) {
        removeSession(sessionId);
      }
    } finally {
      setIsRequestingHint(false);
    }
  }, [client, sessionId, question, isRequestingHint]);

  /**
   * Submits an answer for the current attempt.
   */
  const submitAnswer = useCallback(
    async (answerText: string): Promise<SubmitAnswerResponse | null> => {
      const trimmed = answerText.trim();
      if (!question || !question.attemptId) {
        setSubmitError(
          new ApiError(400, {
            error: 'NO_ACTIVE_ATTEMPT',
            message: 'No active question attempt available for submission.',
            retryable: false,
            timestamp: Date.now(),
          })
        );
        return null;
      }

      if (trimmed.length === 0) {
        setSubmitError(
          new ApiError(400, {
            error: 'VALIDATION_ERROR',
            message: 'Answer cannot be blank. Please enter your explanation or implementation.',
            retryable: false,
            timestamp: Date.now(),
          })
        );
        return null;
      }

      if (trimmed.length > 2000) {
        setSubmitError(
          new ApiError(400, {
            error: 'VALIDATION_ERROR',
            message: 'Answer exceeds maximum allowed length of 2000 characters.',
            retryable: false,
            timestamp: Date.now(),
          })
        );
        return null;
      }

      if (isSubmittingAnswer) {
        return null; // Prevent duplicate in-flight submissions
      }

      setIsSubmittingAnswer(true);
      setSubmitError(null);

      try {
        const result = await client.submitAnswer(sessionId, {
          attemptId: question.attemptId,
          answer: trimmed,
        });

        // Stale check: if attemptId changed in the meantime, ignore
        if (result.attemptId !== question.attemptId) {
          return null;
        }

        setEvaluationResult(result);

        // Update local session pointer with latest state and mastery
        saveSession({
          sessionId,
          topicId: question.topicId,
          createdAt: Date.now(),
          updatedAt: Date.now(),
          lastKnownState: result.currentState,
          lastKnownMastery: result.currentMastery,
        });

        return result;
      } catch (err: unknown) {
        const apiErr = err instanceof ApiError ? err : ApiError.networkError(err instanceof Error ? err : undefined);
        setSubmitError(apiErr);

        if (apiErr.isRateLimited) {
          setRateLimitCountdown(apiErr.retryAfterSeconds || 5);
        } else if (apiErr.isNotFound || apiErr.isSessionExpired) {
          removeSession(sessionId);
        } else if (apiErr.isDuplicateAttempt) {
          // If attempt was already consumed, refresh current question so UI gets the active attempt
          loadCurrentQuestion();
        }

        return null;
      } finally {
        setIsSubmittingAnswer(false);
      }
    },
    [client, sessionId, question, isSubmittingAnswer, loadCurrentQuestion]
  );

  /**
   * Advances to the next question/stage following evaluation feedback.
   */
  const advanceToNextQuestion = useCallback(async () => {
    setEvaluationResult(null);
    setHints([]);
    setSubmitError(null);
    setHintError(null);
    setIsRequestingHint(false);
    activeAttemptIdRef.current = null;
    await loadCurrentQuestion();
  }, [loadCurrentQuestion]);

  const dismissErrors = useCallback(() => {
    setHintError(null);
    setSubmitError(null);
    setQuestionError(null);
  }, []);

  return {
    question,
    isLoadingQuestion,
    questionError,
    hints,
    isRequestingHint,
    hintError,
    isSubmittingAnswer,
    submitError,
    evaluationResult,
    rateLimitCountdown,
    loadCurrentQuestion,
    requestHint,
    submitAnswer,
    advanceToNextQuestion,
    dismissErrors,
  };
}
