import { describe, it, expect, vi, afterEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { useHealthCheck } from '../../src/hooks/useHealthCheck';
import { ApiClient } from '../../src/api/client';
import { HealthResponse } from '../../src/api/types';
import { ApiError } from '../../src/api/errors';

describe('useHealthCheck', () => {
  const mockHealth: HealthResponse = {
    status: 'UP',
    version: '1.0.0',
    activeTopics: 7,
    aiConfigured: true,
  };

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('loads health data successfully on first attempt without entering waking state', async () => {
    const mockClient = {
      getHealth: vi.fn().mockResolvedValue(mockHealth),
    } as unknown as ApiClient;

    const { result } = renderHook(() => useHealthCheck(mockClient));

    expect(result.current.isLoading).toBe(true);
    expect(result.current.isWakingUp).toBe(false);

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.health).toEqual(mockHealth);
    expect(result.current.isWakingUp).toBe(false);
    expect(result.current.error).toBeNull();
    expect(mockClient.getHealth).toHaveBeenCalledTimes(1);
  });

  it('automatically retries with backoff when transient error occurs and recovers on next attempt', async () => {
    vi.useFakeTimers();
    try {
      const transientError = ApiError.timeout(12000);
      const getHealthMock = vi.fn()
        .mockRejectedValueOnce(transientError)
        .mockResolvedValueOnce(mockHealth);

      const mockClient = { getHealth: getHealthMock } as unknown as ApiClient;

      const { result } = renderHook(() => useHealthCheck(mockClient));

      // Flush attempt 1 rejection
      await act(async () => {
        await Promise.resolve();
      });

      expect(result.current.isWakingUp).toBe(true);
      expect(result.current.isLoading).toBe(true);
      expect(result.current.health).toBeNull();
      expect(result.current.error).toBeNull();

      // Advance first backoff timer (2000ms)
      await act(async () => {
        vi.advanceTimersByTime(2000);
      });

      // Flush attempt 2 resolution
      await act(async () => {
        await Promise.resolve();
      });

      expect(result.current.isWakingUp).toBe(false);
      expect(result.current.isLoading).toBe(false);
      expect(result.current.health).toEqual(mockHealth);
      expect(result.current.error).toBeNull();
      expect(getHealthMock).toHaveBeenCalledTimes(2);
    } finally {
      vi.useRealTimers();
    }
  });

  it('surfaces error when maximum retries are exhausted', async () => {
    vi.useFakeTimers();
    try {
      const transientError = new ApiError(503, {
        error: 'SERVICE_UNAVAILABLE',
        message: 'Backend server starting',
        retryable: true,
        timestamp: Date.now(),
      });

      const getHealthMock = vi.fn().mockRejectedValue(transientError);
      const mockClient = { getHealth: getHealthMock } as unknown as ApiClient;

      const { result } = renderHook(() => useHealthCheck(mockClient));

      // Attempt 1 fails
      await act(async () => { await Promise.resolve(); });
      expect(result.current.isWakingUp).toBe(true);

      // Attempt 2 fails (after 2000ms)
      await act(async () => { vi.advanceTimersByTime(2000); await Promise.resolve(); });
      expect(result.current.isWakingUp).toBe(true);

      // Attempt 3 fails (after 3000ms)
      await act(async () => { vi.advanceTimersByTime(3000); await Promise.resolve(); });
      expect(result.current.isWakingUp).toBe(true);

      // Attempt 4 fails (after 5000ms)
      await act(async () => { vi.advanceTimersByTime(5000); await Promise.resolve(); });

      // Retries exhausted
      expect(result.current.isWakingUp).toBe(false);
      expect(result.current.isLoading).toBe(false);
      expect(result.current.health).toBeNull();
      expect(result.current.error).toBe(transientError);
      expect(getHealthMock).toHaveBeenCalledTimes(4);
    } finally {
      vi.useRealTimers();
    }
  });

  it('does not retry permanent client errors such as HTTP 400 or 401', async () => {
    const clientError = new ApiError(400, {
      error: 'BAD_REQUEST',
      message: 'Invalid request parameters',
      retryable: false,
      timestamp: Date.now(),
    });

    const getHealthMock = vi.fn().mockRejectedValue(clientError);
    const mockClient = { getHealth: getHealthMock } as unknown as ApiClient;

    const { result } = renderHook(() => useHealthCheck(mockClient));

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.isWakingUp).toBe(false);
    expect(result.current.health).toBeNull();
    expect(result.current.error).toBe(clientError);
    expect(getHealthMock).toHaveBeenCalledTimes(1);
  });

  it('cancels scheduled retry timers when unmounted', async () => {
    vi.useFakeTimers();
    try {
      const transientError = ApiError.timeout(12000);
      const getHealthMock = vi.fn().mockRejectedValue(transientError);
      const mockClient = { getHealth: getHealthMock } as unknown as ApiClient;

      const { unmount } = renderHook(() => useHealthCheck(mockClient));

      await act(async () => { await Promise.resolve(); });
      expect(getHealthMock).toHaveBeenCalledTimes(1);

      unmount();

      // Advance timers
      await act(async () => { vi.advanceTimersByTime(10000); });
      // Should NOT have triggered attempt 2
      expect(getHealthMock).toHaveBeenCalledTimes(1);
    } finally {
      vi.useRealTimers();
    }
  });

  it('manual refetch resets attempt count, cancels pending timers, and retries immediately', async () => {
    vi.useFakeTimers();
    try {
      const transientError = ApiError.timeout(12000);

      const getHealthMock = vi.fn()
        .mockRejectedValueOnce(transientError)
        .mockResolvedValueOnce(mockHealth);

      const mockClient = { getHealth: getHealthMock } as unknown as ApiClient;

      const { result } = renderHook(() => useHealthCheck(mockClient));

      await act(async () => { await Promise.resolve(); });
      expect(result.current.isWakingUp).toBe(true);

      // Trigger manual refetch before backoff timer fires
      await act(async () => {
        result.current.refetch();
        await Promise.resolve();
      });

      expect(getHealthMock).toHaveBeenCalledTimes(2);
      expect(result.current.health).toEqual(mockHealth);
      expect(result.current.isWakingUp).toBe(false);
      expect(result.current.isLoading).toBe(false);
      expect(result.current.error).toBeNull();
    } finally {
      vi.useRealTimers();
    }
  });

  it('enforces the 60-second recovery deadline when an attempt consumes most of the available time', async () => {
    vi.useFakeTimers();
    try {
      const baseTime = 1000000;
      vi.setSystemTime(baseTime);

      const timeoutError = ApiError.timeout(12000);
      let attempt1Reject: (err: unknown) => void;
      const attempt1Promise = new Promise((_, reject) => {
        attempt1Reject = reject;
      });

      let attempt2Reject: (err: unknown) => void;
      const attempt2Promise = new Promise((_, reject) => {
        attempt2Reject = reject;
      });

      const getHealthMock = vi.fn()
        .mockImplementationOnce(() => attempt1Promise)
        .mockImplementationOnce(() => attempt2Promise);

      const mockClient = { getHealth: getHealthMock } as unknown as ApiClient;

      const { result } = renderHook(() => useHealthCheck(mockClient));

      // Attempt 1 launched with 12000ms timeout
      expect(getHealthMock).toHaveBeenCalledTimes(1);
      expect(getHealthMock.mock.calls[0][0].timeoutMs).toBe(12000);

      // Attempt 1 consumes 52 seconds before failing
      await act(async () => {
        vi.advanceTimersByTime(52000);
        attempt1Reject(timeoutError);
        await Promise.resolve();
      });

      expect(result.current.isWakingUp).toBe(true);

      // Advance 2s backoff delay (from T=52s to T=54s)
      await act(async () => {
        vi.advanceTimersByTime(2000);
        await Promise.resolve();
      });

      // Attempt 2 launched at T=54s. Remaining budget is 60s - 54s = 6s (6000ms)
      expect(getHealthMock).toHaveBeenCalledTimes(2);
      expect(getHealthMock.mock.calls[1][0].timeoutMs).toBe(6000);

      // Attempt 2 consumes remaining 6 seconds and fails at exactly T=60s
      await act(async () => {
        vi.advanceTimersByTime(6000);
        attempt2Reject(ApiError.timeout(6000));
        await Promise.resolve();
      });

      // Deadline exhausted: stopped immediately after 2 attempts (instead of 4)
      expect(result.current.isWakingUp).toBe(false);
      expect(result.current.isLoading).toBe(false);
      expect(result.current.health).toBeNull();
      expect(result.current.error).toBeInstanceOf(ApiError);
      expect(getHealthMock).toHaveBeenCalledTimes(2);

      // Further time passage does NOT trigger any new attempts
      await act(async () => {
        vi.advanceTimersByTime(60000);
      });
      expect(getHealthMock).toHaveBeenCalledTimes(2);
    } finally {
      vi.useRealTimers();
    }
  });

  it('clamps backoff delay and does not start a new attempt if deadline expires during backoff', async () => {
    vi.useFakeTimers();
    try {
      const baseTime = 2000000;
      vi.setSystemTime(baseTime);

      const timeoutError = ApiError.timeout(12000);
      let attempt1Reject: (err: unknown) => void;
      const attempt1Promise = new Promise((_, reject) => {
        attempt1Reject = reject;
      });

      const getHealthMock = vi.fn().mockImplementationOnce(() => attempt1Promise);
      const mockClient = { getHealth: getHealthMock } as unknown as ApiClient;

      const { result } = renderHook(() => useHealthCheck(mockClient));

      // Attempt 1 consumes 59 seconds before failing
      await act(async () => {
        vi.advanceTimersByTime(59000);
        attempt1Reject(timeoutError);
        await Promise.resolve();
      });

      expect(result.current.isWakingUp).toBe(true);

      // Only 1 second remained in deadline, so 2s backoff delay is clamped to 1s
      // Advancing 1s reaches T=60s
      await act(async () => {
        vi.advanceTimersByTime(1000);
        await Promise.resolve();
      });

      // At T=60s, timer fires, sees remaining budget <= 0, and surfaces error without calling getHealth again
      expect(result.current.isWakingUp).toBe(false);
      expect(result.current.isLoading).toBe(false);
      expect(result.current.error).toBe(timeoutError);
      expect(getHealthMock).toHaveBeenCalledTimes(1);
    } finally {
      vi.useRealTimers();
    }
  });
});
