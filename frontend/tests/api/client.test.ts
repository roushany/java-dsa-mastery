import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ApiClient, TOKEN_HEADER } from '../../src/api/client';
import { ApiError } from '../../src/api/errors';
import {
  HealthResponse,
  CurriculumResponse,
  SessionResponse,
  QuestionResponse,
  HintResponse,
  SubmitAnswerResponse,
  ErrorResponse,
} from '../../src/api/types';

describe('ApiClient', () => {
  let mockFetch: ReturnType<typeof vi.fn>;
  let mockGetToken: ReturnType<typeof vi.fn>;
  let mockOnTokenReceived: ReturnType<typeof vi.fn>;
  let client: ApiClient;

  const validToken = `learner-tester.${Date.now() - 5000}.valid_signature`;

  beforeEach(() => {
    mockFetch = vi.fn();
    mockGetToken = vi.fn().mockReturnValue(validToken);
    mockOnTokenReceived = vi.fn();

    client = new ApiClient({
      baseUrl: 'http://localhost:8080/api/v1',
      timeoutMs: 1000,
      getToken: mockGetToken,
      onTokenReceived: mockOnTokenReceived,
      fetchFn: mockFetch as unknown as typeof fetch,
    });
  });

  const createMockResponse = (
    status: number,
    body: unknown,
    headers: Record<string, string> = {}
  ): Response => {
    const isString = typeof body === 'string';
    const textBody = isString ? body : JSON.stringify(body);
    const defaultHeaders: Record<string, string> = isString ? {} : { 'content-type': 'application/json' };
    const mergedHeaders = new Headers({ ...defaultHeaders, ...headers });

    return {
      ok: status >= 200 && status < 300,
      status,
      statusText: status === 200 ? 'OK' : 'Error',
      headers: mergedHeaders,
      json: async () => (isString ? JSON.parse(body as string) : body),
      text: async () => textBody,
    } as unknown as Response;
  };

  describe('Successful API Requests & Header Verification', () => {
    it('getHealth: performs GET /health without X-Learner-Token header', async () => {
      const mockHealth: HealthResponse = {
        status: 'UP',
        version: '1.0.0',
        activeTopics: 7,
        aiConfigured: true,
      };
      mockFetch.mockResolvedValueOnce(createMockResponse(200, mockHealth));

      const res = await client.getHealth();

      expect(res).toEqual(mockHealth);
      expect(mockFetch).toHaveBeenCalledTimes(1);
      const [url, init] = mockFetch.mock.calls[0];
      expect(url).toBe('http://localhost:8080/api/v1/health');
      expect(init.method).toBe('GET');
      const headers = init.headers as Headers;
      expect(headers.has(TOKEN_HEADER)).toBe(false);
    });

    it('getCurriculum: performs GET /curriculum without X-Learner-Token header', async () => {
      const mockCurriculum: CurriculumResponse = {
        topics: [
          {
            topicId: 'binary-search',
            title: 'Binary Search',
            difficulty: 'BEGINNER',
            prerequisites: ['linear-search'],
            hasContent: true,
            totalStages: 9,
          },
        ],
      };
      mockFetch.mockResolvedValueOnce(createMockResponse(200, mockCurriculum));

      const res = await client.getCurriculum();

      expect(res).toEqual(mockCurriculum);
      const [url, init] = mockFetch.mock.calls[0];
      expect(url).toBe('http://localhost:8080/api/v1/curriculum');
      const headers = init.headers as Headers;
      expect(headers.has(TOKEN_HEADER)).toBe(false);
    });

    it('startSession: captures X-Learner-Token from response header and passes it to callback', async () => {
      const mockSession: SessionResponse = {
        sessionId: 'session-1234',
        learnerId: 'learner-tester',
        topicId: 'binary-search',
        topicTitle: 'Binary Search',
        currentState: 'CONTENT_INTRO',
        mastery: 'NOVICE',
        totalStages: 9,
        completed: false,
      };
      const newIssuedToken = `learner-tester.${Date.now()}.new_sig`;
      mockFetch.mockResolvedValueOnce(
        createMockResponse(201, mockSession, {
          [TOKEN_HEADER]: newIssuedToken,
        })
      );

      const res = await client.startSession({ topicId: 'binary-search' });

      expect(res).toEqual(mockSession);
      expect(mockOnTokenReceived).toHaveBeenCalledWith(newIssuedToken);
    });

    it('getSession: performs GET /sessions/{id} with X-Learner-Token', async () => {
      const mockSession: SessionResponse = {
        sessionId: 'session-1234',
        learnerId: 'learner-tester',
        topicId: 'binary-search',
        topicTitle: 'Binary Search',
        currentState: 'CONCEPT_ASSESSMENT',
        mastery: 'NOVICE',
        totalStages: 9,
        completed: false,
      };
      mockFetch.mockResolvedValueOnce(createMockResponse(200, mockSession));

      const res = await client.getSession('session-1234');

      expect(res).toEqual(mockSession);
      const [url, init] = mockFetch.mock.calls[0];
      expect(url).toBe('http://localhost:8080/api/v1/sessions/session-1234');
      const headers = init.headers as Headers;
      expect(headers.get(TOKEN_HEADER)).toBe(validToken);
    });

    it('getCurrentQuestion: performs GET /sessions/{id}/current-question with X-Learner-Token', async () => {
      const mockQuestion: QuestionResponse = {
        attemptId: 'attempt-1',
        sessionId: 'session-1234',
        topicId: 'binary-search',
        stage: 'CONCEPT_ASSESSMENT',
        difficulty: 'EASY',
        content: 'Explain binary search.',
        hintsUsed: 0,
        isTerminal: false,
      };
      mockFetch.mockResolvedValueOnce(createMockResponse(200, mockQuestion));

      const res = await client.getCurrentQuestion('session-1234');

      expect(res).toEqual(mockQuestion);
      const [url] = mockFetch.mock.calls[0];
      expect(url).toBe('http://localhost:8080/api/v1/sessions/session-1234/current-question');
    });

    it('requestHint: sends POST /sessions/{id}/request-hint with body and X-Learner-Token', async () => {
      const mockHint: HintResponse = {
        attemptId: 'attempt-1',
        hintLevel: 1,
        hintText: 'Consider the array order.',
      };
      mockFetch.mockResolvedValueOnce(createMockResponse(200, mockHint));

      const res = await client.requestHint('session-1234', { attemptId: 'attempt-1' });

      expect(res).toEqual(mockHint);
      const [url, init] = mockFetch.mock.calls[0];
      expect(url).toBe('http://localhost:8080/api/v1/sessions/session-1234/request-hint');
      expect(init.method).toBe('POST');
      expect(JSON.parse(init.body as string)).toEqual({ attemptId: 'attempt-1' });
      const headers = init.headers as Headers;
      expect(headers.get('Content-Type')).toBe('application/json');
      expect(headers.get(TOKEN_HEADER)).toBe(validToken);
    });

    it('submitAnswer: sends POST /sessions/{id}/submit-answer with body and X-Learner-Token', async () => {
      const mockSubmitRes: SubmitAnswerResponse = {
        attemptId: 'attempt-1',
        evaluation: 'PASS',
        feedback: 'Evaluation completed: PASS',
        misconceptionDetected: null,
        currentState: 'DERIVE',
        currentMastery: 'NOVICE',
        stageAdvanced: true,
        hintsUsed: 0,
      };
      mockFetch.mockResolvedValueOnce(createMockResponse(200, mockSubmitRes));

      const res = await client.submitAnswer('session-1234', {
        attemptId: 'attempt-1',
        answer: 'Correct explanation',
      });

      expect(res).toEqual(mockSubmitRes);
      const [url, init] = mockFetch.mock.calls[0];
      expect(url).toBe('http://localhost:8080/api/v1/sessions/session-1234/submit-answer');
      expect(init.method).toBe('POST');
      expect(JSON.parse(init.body as string)).toEqual({
        attemptId: 'attempt-1',
        answer: 'Correct explanation',
      });
      const headers = init.headers as Headers;
      expect(headers.get(TOKEN_HEADER)).toBe(validToken);
    });
  });

  describe('Learner Token Contract & Security Verification', () => {
    it('throws ApiError 401 locally without making network call if token is missing on protected endpoint', async () => {
      mockGetToken.mockReturnValue(null);

      await expect(client.getSession('session-1234')).rejects.toThrow(ApiError);
      expect(mockFetch).not.toHaveBeenCalled();

      try {
        await client.getSession('session-1234');
      } catch (err: unknown) {
        expect(err).toBeInstanceOf(ApiError);
        const apiErr = err as ApiError;
        expect(apiErr.status).toBe(401);
        expect(apiErr.isUnauthorized).toBe(true);
      }
    });

    it('captures token strictly from X-Learner-Token response header, never from response body', async () => {
      const mockSession: SessionResponse = {
        sessionId: 'session-unique-uuid-1234',
        learnerId: 'learner-alice',
        topicId: 'linear-search',
        topicTitle: 'Linear Search',
        currentState: 'CONTENT_INTRO',
        mastery: 'NOVICE',
        totalStages: 1,
        completed: false,
      };
      const headerToken = `learner-alice.${Date.now()}.header_signature`;

      mockFetch.mockResolvedValueOnce(
        createMockResponse(201, mockSession, {
          [TOKEN_HEADER]: headerToken,
        })
      );

      await client.startSession({ topicId: 'linear-search' });

      // Must capture header token exactly, NOT learnerId or sessionId from body
      expect(mockOnTokenReceived).toHaveBeenCalledTimes(1);
      expect(mockOnTokenReceived).toHaveBeenCalledWith(headerToken);
      expect(mockOnTokenReceived).not.toHaveBeenCalledWith('session-unique-uuid-1234');
      expect(mockOnTokenReceived).not.toHaveBeenCalledWith('learner-alice');
    });

    it('does not invoke onTokenReceived if X-Learner-Token header is absent in response', async () => {
      const mockSession: SessionResponse = {
        sessionId: 'session-no-header',
        learnerId: 'learner-bob',
        topicId: 'linear-search',
        topicTitle: 'Linear Search',
        currentState: 'CONTENT_INTRO',
        mastery: 'NOVICE',
        totalStages: 1,
        completed: false,
      };

      // Response without X-Learner-Token header
      mockFetch.mockResolvedValueOnce(createMockResponse(200, mockSession));

      await client.startSession({ topicId: 'linear-search' });

      expect(mockOnTokenReceived).not.toHaveBeenCalled();
    });

    it('transmits exact stored token in X-Learner-Token header without modification', async () => {
      const complexToken = `learner-special_123.${Date.now() - 5000}.aBcDeFgHiJkLmNoPqRsTuVwXyZ_-1234567890`;
      mockGetToken.mockReturnValue(complexToken);

      mockFetch.mockResolvedValueOnce(
        createMockResponse(200, {
          sessionId: 'session-1234',
          learnerId: 'learner-special_123',
          topicId: 'binary-search',
          topicTitle: 'Binary Search',
          currentState: 'CONCEPT_ASSESSMENT',
          mastery: 'NOVICE',
          totalStages: 9,
          completed: false,
        })
      );

      await client.getSession('session-1234');

      const [, init] = mockFetch.mock.calls[0];
      const headers = init.headers as Headers;
      expect(headers.get(TOKEN_HEADER)).toBe(complexToken);
    });

    it('never treats a 401 Unauthorized or 403 Forbidden as successful', async () => {
      const errorPayload: ErrorResponse = {
        error: 'UNAUTHORIZED',
        message: 'Invalid or expired learner token.',
        retryable: false,
        timestamp: Date.now(),
      };
      mockFetch.mockResolvedValueOnce(createMockResponse(401, errorPayload));

      await expect(client.getSession('session-1')).rejects.toThrow(ApiError);
    });
  });

  describe('Error Response Handling', () => {
    it('handles 400 VALIDATION_ERROR', async () => {
      const errorPayload: ErrorResponse = {
        error: 'VALIDATION_ERROR',
        message: 'topicId: must not be blank',
        retryable: false,
        timestamp: Date.now(),
      };
      mockFetch.mockResolvedValueOnce(createMockResponse(400, errorPayload));

      await expect(client.startSession({ topicId: '' })).rejects.toThrow(ApiError);
    });

    it('handles 401 UNAUTHORIZED', async () => {
      const errorPayload: ErrorResponse = {
        error: 'UNAUTHORIZED',
        message: 'Invalid or expired learner token.',
        retryable: false,
        timestamp: Date.now(),
      };
      mockFetch.mockResolvedValueOnce(createMockResponse(401, errorPayload));

      try {
        await client.getSession('session-1');
        expect.unreachable('Should have thrown ApiError');
      } catch (err: unknown) {
        expect(err).toBeInstanceOf(ApiError);
        const apiErr = err as ApiError;
        expect(apiErr.status).toBe(401);
        expect(apiErr.isUnauthorized).toBe(true);
      }
    });

    it('handles 403 FORBIDDEN (cross-learner access)', async () => {
      const errorPayload: ErrorResponse = {
        error: 'FORBIDDEN',
        message: 'Cross-learner access denied.',
        retryable: false,
        timestamp: Date.now(),
      };
      mockFetch.mockResolvedValueOnce(createMockResponse(403, errorPayload));

      try {
        await client.getSession('session-1');
        expect.unreachable('Should have thrown ApiError');
      } catch (err: unknown) {
        expect(err).toBeInstanceOf(ApiError);
        const apiErr = err as ApiError;
        expect(apiErr.status).toBe(403);
        expect(apiErr.isForbidden).toBe(true);
      }
    });

    it('handles 404 SESSION_NOT_FOUND', async () => {
      const errorPayload: ErrorResponse = {
        error: 'SESSION_NOT_FOUND',
        message: 'Session not found: session-xyz',
        retryable: false,
        timestamp: Date.now(),
      };
      mockFetch.mockResolvedValueOnce(createMockResponse(404, errorPayload));

      try {
        await client.getSession('session-xyz');
        expect.unreachable('Should have thrown ApiError');
      } catch (err: unknown) {
        expect(err).toBeInstanceOf(ApiError);
        const apiErr = err as ApiError;
        expect(apiErr.status).toBe(404);
        expect(apiErr.isNotFound).toBe(true);
      }
    });

    it('handles 409 DUPLICATE_ATTEMPT and STALE_SESSION_VERSION', async () => {
      const errorPayload: ErrorResponse = {
        error: 'DUPLICATE_ATTEMPT',
        message: 'Attempt already consumed.',
        retryable: false,
        timestamp: Date.now(),
      };
      mockFetch.mockResolvedValueOnce(createMockResponse(409, errorPayload));

      try {
        await client.submitAnswer('session-1', { attemptId: 'att-1', answer: 'new ans' });
        expect.unreachable('Should have thrown ApiError');
      } catch (err: unknown) {
        expect(err).toBeInstanceOf(ApiError);
        const apiErr = err as ApiError;
        expect(apiErr.status).toBe(409);
        expect(apiErr.isDuplicateAttempt).toBe(true);
      }
    });

    it('handles 410 SESSION_EXPIRED', async () => {
      const errorPayload: ErrorResponse = {
        error: 'SESSION_EXPIRED',
        message: 'Session session-1 has expired.',
        retryable: false,
        timestamp: Date.now(),
      };
      mockFetch.mockResolvedValueOnce(createMockResponse(410, errorPayload));

      try {
        await client.getCurrentQuestion('session-1');
        expect.unreachable('Should have thrown ApiError');
      } catch (err: unknown) {
        expect(err).toBeInstanceOf(ApiError);
        const apiErr = err as ApiError;
        expect(apiErr.status).toBe(410);
        expect(apiErr.isSessionExpired).toBe(true);
      }
    });

    it('handles 429 RATE_LIMIT_EXCEEDED and parses Retry-After header', async () => {
      const errorPayload: ErrorResponse = {
        error: 'RATE_LIMIT_EXCEEDED',
        message: 'Rate limit exceeded.',
        retryable: true,
        timestamp: Date.now(),
      };
      mockFetch.mockResolvedValueOnce(
        createMockResponse(429, errorPayload, {
          'Retry-After': '7',
        })
      );

      try {
        await client.submitAnswer('session-1', { attemptId: 'att-1', answer: 'ans' });
        expect.unreachable('Should have thrown ApiError');
      } catch (err: unknown) {
        expect(err).toBeInstanceOf(ApiError);
        const apiErr = err as ApiError;
        expect(apiErr.status).toBe(429);
        expect(apiErr.isRateLimited).toBe(true);
        expect(apiErr.retryAfterSeconds).toBe(7);
        expect(apiErr.isRetryable).toBe(true);
      }
    });

    it('handles 500 INTERNAL_SERVER_ERROR', async () => {
      const errorPayload: ErrorResponse = {
        error: 'INTERNAL_SERVER_ERROR',
        message: 'An unexpected error occurred.',
        retryable: true,
        timestamp: Date.now(),
      };
      mockFetch.mockResolvedValueOnce(createMockResponse(500, errorPayload));

      try {
        await client.getHealth();
        expect.unreachable('Should have thrown ApiError');
      } catch (err: unknown) {
        expect(err).toBeInstanceOf(ApiError);
        const apiErr = err as ApiError;
        expect(apiErr.status).toBe(500);
        expect(apiErr.isRetryable).toBe(true);
      }
    });

    it('handles 503 PROVIDER_UNAVAILABLE and 504 PROVIDER_TIMEOUT', async () => {
      const errorPayload: ErrorResponse = {
        error: 'PROVIDER_TIMEOUT',
        message: 'AI Provider timed out.',
        retryable: true,
        timestamp: Date.now(),
      };
      mockFetch.mockResolvedValueOnce(createMockResponse(504, errorPayload));

      try {
        await client.requestHint('session-1', { attemptId: 'att-1' });
        expect.unreachable('Should have thrown ApiError');
      } catch (err: unknown) {
        expect(err).toBeInstanceOf(ApiError);
        const apiErr = err as ApiError;
        expect(apiErr.status).toBe(504);
        expect(apiErr.isProviderTimeout).toBe(true);
        expect(apiErr.isRetryable).toBe(true);
      }
    });
  });

  describe('Malformed Responses and Network Failures', () => {
    it('handles non-JSON HTML error pages (e.g. 502 Bad Gateway from reverse proxy)', async () => {
      const htmlBody = '<html><body>502 Bad Gateway</body></html>';
      const fakeResponse = {
        ok: false,
        status: 502,
        statusText: 'Bad Gateway',
        headers: new Headers({ 'content-type': 'text/html' }),
        json: async () => {
          throw new SyntaxError('Unexpected token <');
        },
        text: async () => htmlBody,
      } as unknown as Response;
      mockFetch.mockResolvedValueOnce(fakeResponse);

      try {
        await client.getHealth();
        expect.unreachable('Should have thrown ApiError');
      } catch (err: unknown) {
        expect(err).toBeInstanceOf(ApiError);
        const apiErr = err as ApiError;
        expect(apiErr.status).toBe(502);
        expect(apiErr.errorData.error).toBe('HTTP_502');
        expect(apiErr.isRetryable).toBe(true);
      }
    });

    it('handles 200 response with malformed JSON body', async () => {
      const fakeResponse = {
        ok: true,
        status: 200,
        statusText: 'OK',
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => {
          throw new SyntaxError('Unexpected end of JSON input');
        },
      } as unknown as Response;
      mockFetch.mockResolvedValueOnce(fakeResponse);
      try {
        await client.getHealth();
        expect.unreachable('Should have thrown ApiError');
      } catch (err: unknown) {
        expect(err).toBeInstanceOf(ApiError);
        const apiErr = err as ApiError;
        expect(apiErr.errorCode).toBe('MALFORMED_JSON');
        expect(apiErr.status).toBe(200);
      }
    });

    it('handles network failure (DNS / offline) cleanly as ApiError', async () => {
      mockFetch.mockRejectedValueOnce(new TypeError('Failed to fetch'));

      try {
        await client.getHealth();
        expect.unreachable('Should have thrown ApiError');
      } catch (err: unknown) {
        expect(err).toBeInstanceOf(ApiError);
        const apiErr = err as ApiError;
        expect(apiErr.status).toBe(0);
        expect(apiErr.errorCode).toBe('NETWORK_ERROR');
        expect(apiErr.isRetryable).toBe(true);
      }
    });

    it('handles request timeout (AbortError) cleanly as 504 REQUEST_TIMEOUT', async () => {
      const abortError = new Error('The operation was aborted');
      abortError.name = 'AbortError';
      mockFetch.mockRejectedValueOnce(abortError);

      try {
        await client.getHealth();
        expect.unreachable('Should have thrown ApiError');
      } catch (err: unknown) {
        expect(err).toBeInstanceOf(ApiError);
        const apiErr = err as ApiError;
        expect(apiErr.status).toBe(504);
        expect(apiErr.errorCode).toBe('REQUEST_TIMEOUT');
        expect(apiErr.isRetryable).toBe(true);
      }
    });
  });
});
