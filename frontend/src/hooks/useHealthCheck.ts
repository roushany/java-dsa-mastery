import { useState, useEffect, useCallback, useRef } from 'react';
import { HealthResponse } from '../api/types';
import { ApiError } from '../api/errors';
import { ApiClient, defaultApiClient } from '../api/client';

export const HEALTH_ATTEMPT_TIMEOUT_MS = 12000;
export const HEALTH_RETRY_SCHEDULE_MS = [2000, 3000, 5000, 5000];
export const MAX_HEALTH_ATTEMPTS = 4;
export const MAX_RECOVERY_WINDOW_MS = 60000;

export interface UseHealthCheckResult {
  health: HealthResponse | null;
  isLoading: boolean;
  isWakingUp: boolean;
  error: ApiError | null;
  refetch: () => void;
}

export function useHealthCheck(client: ApiClient = defaultApiClient): UseHealthCheckResult {
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isWakingUp, setIsWakingUp] = useState<boolean>(false);
  const [error, setError] = useState<ApiError | null>(null);

  const attemptCountRef = useRef<number>(0);
  const startTimeRef = useRef<number>(Date.now());
  const timerIdRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const activeControllerRef = useRef<AbortController | null>(null);
  const isMountedRef = useRef<boolean>(true);
  const lastErrorRef = useRef<ApiError | null>(null);

  // Helper to clear pending retry timers and cancel in-flight requests
  const cancelActiveWork = useCallback(() => {
    if (timerIdRef.current !== null) {
      clearTimeout(timerIdRef.current);
      timerIdRef.current = null;
    }
    if (activeControllerRef.current !== null) {
      activeControllerRef.current.abort();
      activeControllerRef.current = null;
    }
  }, []);

  const executeAttempt = useCallback(async () => {
    if (!isMountedRef.current) return;

    const now = Date.now();
    const elapsedAtStart = now - startTimeRef.current;
    const remainingAtStart = MAX_RECOVERY_WINDOW_MS - elapsedAtStart;

    // Do not start a new attempt if the deadline has expired
    if (remainingAtStart <= 0) {
      cancelActiveWork();
      setHealth(null);
      setIsWakingUp(false);
      setIsLoading(false);
      setError(lastErrorRef.current ?? ApiError.timeout(MAX_RECOVERY_WINDOW_MS));
      return;
    }

    // Ensure any previous in-flight controller is aborted
    if (activeControllerRef.current !== null) {
      activeControllerRef.current.abort();
    }

    const controller = new AbortController();
    activeControllerRef.current = controller;

    // Clamp attempt timeout to remaining budget; avoids exceeding deadline
    const timeoutMs = Math.min(HEALTH_ATTEMPT_TIMEOUT_MS, remainingAtStart);

    try {
      const data = await client.getHealth({
        timeoutMs,
        signal: controller.signal,
      });

      if (!isMountedRef.current) return;

      // Successful health check: reset retry progression
      setHealth(data);
      setError(null);
      setIsWakingUp(false);
      setIsLoading(false);
      attemptCountRef.current = 0;
      lastErrorRef.current = null;
    } catch (rawErr: unknown) {
      if (!isMountedRef.current) return;

      // If aborted by our own cancellation (unmount or manual reset), do not advance retries
      if (controller.signal.aborted) {
        return;
      }

      const apiErr = rawErr instanceof ApiError
        ? rawErr
        : ApiError.networkError(rawErr instanceof Error ? rawErr : undefined);

      lastErrorRef.current = apiErr;

      // Check if the error is a permanent client error (e.g. 400 Bad Request, 401 Unauthorized, 403, 404)
      const isPermanentClientError =
        apiErr.status >= 400 && apiErr.status < 500 && apiErr.status !== 408 && apiErr.status !== 429;

      const currentElapsedMs = Date.now() - startTimeRef.current;
      const remainingMs = MAX_RECOVERY_WINDOW_MS - currentElapsedMs;

      const canRetry =
        !isPermanentClientError &&
        attemptCountRef.current + 1 < MAX_HEALTH_ATTEMPTS &&
        remainingMs > 0;

      if (canRetry) {
        const attemptIndex = attemptCountRef.current;
        attemptCountRef.current += 1;
        const scheduledDelay =
          HEALTH_RETRY_SCHEDULE_MS[Math.min(attemptIndex, HEALTH_RETRY_SCHEDULE_MS.length - 1)];

        // Clamp backoff delay so it cannot extend past the deadline
        const delayMs = Math.min(scheduledDelay, remainingMs);

        setIsWakingUp(true);
        setIsLoading(true);
        setError(null);
        setHealth(null);

        timerIdRef.current = setTimeout(() => {
          timerIdRef.current = null;
          executeAttempt();
        }, delayMs);
      } else {
        // Exhausted retries, permanent error, or deadline expired
        cancelActiveWork();
        setHealth(null);
        setIsWakingUp(false);
        setIsLoading(false);
        setError(apiErr);
      }
    } finally {
      if (activeControllerRef.current === controller) {
        activeControllerRef.current = null;
      }
    }
  }, [client, cancelActiveWork]);

  const refetch = useCallback(() => {
    cancelActiveWork();
    attemptCountRef.current = 0;
    startTimeRef.current = Date.now();
    lastErrorRef.current = null;
    setIsWakingUp(false);
    setIsLoading(true);
    setError(null);
    executeAttempt();
  }, [cancelActiveWork, executeAttempt]);

  useEffect(() => {
    isMountedRef.current = true;
    startTimeRef.current = Date.now();
    attemptCountRef.current = 0;
    executeAttempt();

    return () => {
      isMountedRef.current = false;
      cancelActiveWork();
    };
  }, [executeAttempt, cancelActiveWork]);

  return { health, isLoading, isWakingUp, error, refetch };
}
