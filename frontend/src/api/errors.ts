import { ErrorResponse } from './types';

/**
 * Custom error class wrapping structured error responses from the backend
 * (GlobalExceptionHandler) and low-level network/timeout failures.
 */
export class ApiError extends Error {
  public readonly status: number;
  public readonly errorData: ErrorResponse;
  public readonly retryAfterSeconds?: number;

  constructor(status: number, errorData: ErrorResponse, retryAfterSeconds?: number) {
    super(errorData.message || `API request failed with status ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.errorData = errorData;
    this.retryAfterSeconds = retryAfterSeconds;

    // Maintain proper prototype chain for instanceof checks
    Object.setPrototypeOf(this, ApiError.prototype);
  }

  get errorCode(): string {
    return this.errorData.error;
  }

  get isUnauthorized(): boolean {
    return this.status === 401 || this.errorData.error === 'UNAUTHORIZED';
  }

  get isForbidden(): boolean {
    return this.status === 403 || this.errorData.error === 'FORBIDDEN';
  }

  get isNotFound(): boolean {
    return this.status === 404 || this.errorData.error === 'SESSION_NOT_FOUND' || this.errorData.error === 'NOT_FOUND';
  }

  get isDuplicateAttempt(): boolean {
    return this.status === 409 && this.errorData.error === 'DUPLICATE_ATTEMPT';
  }

  get isStaleSessionVersion(): boolean {
    return this.status === 409 && this.errorData.error === 'STALE_SESSION_VERSION';
  }

  get isSessionExpired(): boolean {
    return this.status === 410 || this.errorData.error === 'SESSION_EXPIRED';
  }

  get isRateLimited(): boolean {
    return this.status === 429 || this.errorData.error === 'RATE_LIMIT_EXCEEDED';
  }

  get isProviderUnavailable(): boolean {
    return this.status === 503 || this.errorData.error === 'PROVIDER_UNAVAILABLE';
  }

  get isProviderTimeout(): boolean {
    return this.status === 504 || this.errorData.error === 'PROVIDER_TIMEOUT';
  }

  get isRetryable(): boolean {
    return this.errorData.retryable || this.status === 429 || this.status >= 500;
  }

  get isNetworkError(): boolean {
    return this.status === 0 || this.errorData.error === 'NETWORK_ERROR';
  }

  /**
   * Factory for synthesized network errors (DNS, offline, connection refused)
   */
  public static networkError(originalError?: Error): ApiError {
    return new ApiError(0, {
      error: 'NETWORK_ERROR',
      message: originalError?.message || 'Unable to connect to server. Please check your network connection.',
      retryable: true,
      timestamp: Date.now(),
    });
  }

  /**
   * Factory for client-side request timeout
   */
  public static timeout(timeoutMs: number): ApiError {
    return new ApiError(504, {
      error: 'REQUEST_TIMEOUT',
      message: `Request timed out after ${timeoutMs}ms. Please retry.`,
      retryable: true,
      timestamp: Date.now(),
    });
  }
}
