import { ApiError } from './errors';
import { ENDPOINTS } from './endpoints';
import {
  CurriculumResponse,
  ErrorResponse,
  HealthResponse,
  HintResponse,
  QuestionResponse,
  RequestHintRequest,
  SessionResponse,
  StartSessionRequest,
  SubmitAnswerRequest,
  SubmitAnswerResponse,
} from './types';
import { getStoredToken, storeToken } from '../storage/tokenStorage';

export const TOKEN_HEADER = 'X-Learner-Token';
export const DEFAULT_TIMEOUT_MS = 15000;

export interface ApiClientConfig {
  baseUrl?: string;
  timeoutMs?: number;
  getToken?: () => string | null;
  onTokenReceived?: (token: string) => void;
  fetchFn?: typeof fetch;
}

/**
 * Robust, typed REST client for the Java DSA Mastery backend.
 */
export class ApiClient {
  private readonly baseUrl: string;
  private readonly timeoutMs: number;
  private readonly getToken: () => string | null;
  private readonly onTokenReceived: (token: string) => void;
  private readonly fetchFn: typeof fetch;

  constructor(config: ApiClientConfig = {}) {
    // Priority: Explicit config > Vite env var > Safe local development default
    const envBase = typeof import.meta !== 'undefined' && import.meta.env
      ? import.meta.env.VITE_API_BASE_URL
      : undefined;

    this.baseUrl = (config.baseUrl ?? envBase ?? '/api/v1').replace(/\/+$/, '');
    this.timeoutMs = config.timeoutMs ?? DEFAULT_TIMEOUT_MS;
    this.getToken = config.getToken ?? getStoredToken;
    this.onTokenReceived = config.onTokenReceived ?? storeToken;
    this.fetchFn = config.fetchFn ?? (typeof window !== 'undefined' ? window.fetch.bind(window) : fetch);
  }

  // ==========================================
  // Public Endpoint Methods
  // ==========================================

  /**
   * GET /api/v1/health (Public)
   */
  public async getHealth(options: { timeoutMs?: number; signal?: AbortSignal } = {}): Promise<HealthResponse> {
    return this.request<HealthResponse>({
      endpoint: ENDPOINTS.health(),
      method: 'GET',
      requiresAuth: false,
      timeoutMs: options.timeoutMs,
      signal: options.signal,
    });
  }

  /**
   * GET /api/v1/curriculum (Public)
   */
  public async getCurriculum(): Promise<CurriculumResponse> {
    return this.request<CurriculumResponse>({
      endpoint: ENDPOINTS.curriculum(),
      method: 'GET',
      requiresAuth: false,
    });
  }

  /**
   * POST /api/v1/sessions
   * Optional token: passes token if already available; captures and stores incoming token.
   */
  public async startSession(
    request: StartSessionRequest,
    tokenOverride?: string
  ): Promise<SessionResponse> {
    const token = tokenOverride ?? this.getToken();
    return this.request<SessionResponse>({
      endpoint: ENDPOINTS.sessions(),
      method: 'POST',
      body: request,
      requiresAuth: false, // Optional auth
      explicitToken: token ?? undefined,
    });
  }

  /**
   * GET /api/v1/sessions/{sessionId} (Protected)
   */
  public async getSession(sessionId: string): Promise<SessionResponse> {
    return this.request<SessionResponse>({
      endpoint: ENDPOINTS.session(sessionId),
      method: 'GET',
      requiresAuth: true,
    });
  }

  /**
   * GET /api/v1/sessions/{sessionId}/current-question (Protected)
   */
  public async getCurrentQuestion(sessionId: string): Promise<QuestionResponse> {
    return this.request<QuestionResponse>({
      endpoint: ENDPOINTS.currentQuestion(sessionId),
      method: 'GET',
      requiresAuth: true,
    });
  }

  /**
   * POST /api/v1/sessions/{sessionId}/request-hint (Protected)
   */
  public async requestHint(
    sessionId: string,
    request: RequestHintRequest
  ): Promise<HintResponse> {
    return this.request<HintResponse>({
      endpoint: ENDPOINTS.requestHint(sessionId),
      method: 'POST',
      body: request,
      requiresAuth: true,
    });
  }

  /**
   * POST /api/v1/sessions/{sessionId}/submit-answer (Protected)
   */
  public async submitAnswer(
    sessionId: string,
    request: SubmitAnswerRequest
  ): Promise<SubmitAnswerResponse> {
    return this.request<SubmitAnswerResponse>({
      endpoint: ENDPOINTS.submitAnswer(sessionId),
      method: 'POST',
      body: request,
      requiresAuth: true,
    });
  }

  // ==========================================
  // Low-level HTTP Transport & Interception
  // ==========================================

  private async request<T>(options: {
    endpoint: string;
    method: 'GET' | 'POST';
    body?: unknown;
    requiresAuth: boolean;
    explicitToken?: string;
    timeoutMs?: number;
    signal?: AbortSignal;
  }): Promise<T> {
    const url = `${this.baseUrl}${options.endpoint}`;
    const headers = new Headers();

    if (options.body !== undefined) {
      headers.set('Content-Type', 'application/json');
    }

    // Determine token to inject
    const token = options.explicitToken ?? (options.requiresAuth ? this.getToken() : null);

    if (token) {
      headers.set(TOKEN_HEADER, token);
    } else if (options.requiresAuth) {
      // Fast-fail on client if a strictly protected endpoint is called without any token
      throw new ApiError(401, {
        error: 'UNAUTHORIZED',
        message: `Missing required ${TOKEN_HEADER} header for protected endpoint: ${options.endpoint}`,
        retryable: false,
        timestamp: Date.now(),
      });
    }

    const effectiveTimeout = options.timeoutMs ?? this.timeoutMs;
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), effectiveTimeout);

    const onExternalAbort = () => {
      controller.abort();
    };

    if (options.signal) {
      if (options.signal.aborted) {
        controller.abort();
      } else {
        options.signal.addEventListener('abort', onExternalAbort, { once: true });
      }
    }

    let response: Response;
    try {
      response = await this.fetchFn(url, {
        method: options.method,
        headers,
        body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
        signal: controller.signal,
      });
    } catch (err: unknown) {
      clearTimeout(timeoutId);
      if (options.signal) {
        options.signal.removeEventListener('abort', onExternalAbort);
      }

      if (options.signal?.aborted) {
        throw ApiError.networkError(new Error('Request cancelled by caller'));
      }

      if (err instanceof Error && err.name === 'AbortError') {
        throw ApiError.timeout(effectiveTimeout);
      }

      throw ApiError.networkError(err instanceof Error ? err : undefined);
    } finally {
      clearTimeout(timeoutId);
      if (options.signal) {
        options.signal.removeEventListener('abort', onExternalAbort);
      }
    }

    // Intercept and persist newly issued or rotated capability tokens
    const incomingToken = response.headers.get(TOKEN_HEADER);
    if (incomingToken) {
      this.onTokenReceived(incomingToken);
    }

    // Successful response handling
    if (response.ok) {
      if (response.status === 204) {
        return {} as T;
      }
      return this.parseJsonSafe<T>(response);
    }

    // Error response handling
    await this.handleErrorResponse(response);
    throw new Error('Unreachable');
  }

  private async parseJsonSafe<T>(response: Response): Promise<T> {
    try {
      return (await response.json()) as T;
    } catch {
      throw new ApiError(response.status, {
        error: 'MALFORMED_JSON',
        message: 'Server returned a 2xx success response but body was not valid JSON.',
        retryable: false,
        timestamp: Date.now(),
      });
    }
  }

  private async handleErrorResponse(response: Response): Promise<never> {
    let errorData: ErrorResponse;

    try {
      const parsed = await response.json();
      if (parsed && typeof parsed === 'object' && typeof parsed.error === 'string') {
        errorData = parsed as ErrorResponse;
      } else {
        errorData = {
          error: `HTTP_${response.status}`,
          message: response.statusText || 'Unexpected response format',
          retryable: response.status >= 500,
          timestamp: Date.now(),
        };
      }
    } catch {
      // Non-JSON response (e.g. proxy HTML error pages)
      errorData = {
        error: `HTTP_${response.status}`,
        message: response.statusText || `Request failed with HTTP status ${response.status}`,
        retryable: response.status >= 500,
        timestamp: Date.now(),
      };
    }

    // Extract Retry-After header on 429 rate limit responses
    let retryAfterSeconds: number | undefined;
    const retryHeader = response.headers.get('Retry-After');
    if (retryHeader) {
      const parsedSec = parseInt(retryHeader, 10);
      if (!Number.isNaN(parsedSec) && parsedSec > 0) {
        retryAfterSeconds = parsedSec;
      }
    }

    throw new ApiError(response.status, errorData, retryAfterSeconds);
  }
}

// Singleton default export for application usage
export const defaultApiClient = new ApiClient();
