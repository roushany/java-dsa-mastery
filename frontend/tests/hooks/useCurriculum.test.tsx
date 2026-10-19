import { describe, it, expect, vi } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { useCurriculum } from '../../src/hooks/useCurriculum';
import { ApiClient } from '../../src/api/client';
import { CurriculumResponse, TopicSummaryDto } from '../../src/api/types';
import { ApiError } from '../../src/api/errors';

describe('useCurriculum hook', () => {
  const mockTopics: TopicSummaryDto[] = [
    {
      topicId: 'linear-search',
      title: 'Linear Search',
      difficulty: 'BEGINNER',
      totalStages: 1,
      prerequisites: [],
      hasContent: true,
    },
    {
      topicId: 'binary-search',
      title: 'Binary Search',
      difficulty: 'INTERMEDIATE',
      totalStages: 9,
      prerequisites: ['linear-search'],
      hasContent: true,
    },
  ];

  const mockCurriculumResponse: CurriculumResponse = {
    topics: mockTopics,
  };

  it('loads curriculum topics successfully', async () => {
    const mockClient = {
      getCurriculum: vi.fn().mockResolvedValue(mockCurriculumResponse),
    } as unknown as ApiClient;

    const { result } = renderHook(() => useCurriculum(mockClient));

    expect(result.current.isLoading).toBe(true);
    expect(result.current.topics).toEqual([]);
    expect(result.current.error).toBeNull();

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.topics).toEqual(mockTopics);
    expect(result.current.error).toBeNull();
    expect(mockClient.getCurriculum).toHaveBeenCalledTimes(1);
  });

  it('handles ApiError from backend cleanly', async () => {
    const apiError = new ApiError(500, {
      error: 'INTERNAL_SERVER_ERROR',
      message: 'Failed to load curriculum from database',
      retryable: true,
      timestamp: Date.now(),
    });

    const mockClient = {
      getCurriculum: vi.fn().mockRejectedValue(apiError),
    } as unknown as ApiClient;

    const { result } = renderHook(() => useCurriculum(mockClient));

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.topics).toEqual([]);
    expect(result.current.error).toBe(apiError);
    expect(result.current.error?.message).toBe('Failed to load curriculum from database');
  });

  it('handles generic network exception cleanly', async () => {
    const genericError = new Error('Network timeout');

    const mockClient = {
      getCurriculum: vi.fn().mockRejectedValue(genericError),
    } as unknown as ApiClient;

    const { result } = renderHook(() => useCurriculum(mockClient));

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.topics).toEqual([]);
    expect(result.current.error).toBeInstanceOf(ApiError);
    expect(result.current.error?.isNetworkError).toBe(true);
  });

  it('handles defensive empty or missing topics field in response', async () => {
    const malformedResponse = {} as unknown as CurriculumResponse;

    const mockClient = {
      getCurriculum: vi.fn().mockResolvedValue(malformedResponse),
    } as unknown as ApiClient;

    const { result } = renderHook(() => useCurriculum(mockClient));

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.topics).toEqual([]);
    expect(result.current.error).toBeNull();
  });

  it('allows manual refetching of curriculum data', async () => {
    const mockClient = {
      getCurriculum: vi
        .fn()
        .mockRejectedValueOnce(new Error('Connection glitch'))
        .mockResolvedValueOnce(mockCurriculumResponse),
    } as unknown as ApiClient;

    const { result } = renderHook(() => useCurriculum(mockClient));

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.error).not.toBeNull();
    expect(result.current.topics).toEqual([]);

    await act(async () => {
      result.current.refetch();
    });

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.error).toBeNull();
    expect(result.current.topics).toEqual(mockTopics);
    expect(mockClient.getCurriculum).toHaveBeenCalledTimes(2);
  });

  it('recovers automatically when isHealthy becomes true after initial failure', async () => {
    const transientError = ApiError.timeout(12000);

    const getCurriculumMock = vi.fn()
      .mockRejectedValueOnce(transientError)
      .mockResolvedValueOnce(mockCurriculumResponse);

    const mockClient = {
      getCurriculum: getCurriculumMock,
    } as unknown as ApiClient;

    const { result, rerender } = renderHook(
      ({ isHealthy }) => useCurriculum(mockClient, isHealthy),
      { initialProps: { isHealthy: false } }
    );

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.error).toBe(transientError);
    expect(result.current.topics).toEqual([]);
    expect(getCurriculumMock).toHaveBeenCalledTimes(1);

    // Backend becomes healthy
    rerender({ isHealthy: true });

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.error).toBeNull();
    expect(result.current.topics).toEqual(mockTopics);
    expect(getCurriculumMock).toHaveBeenCalledTimes(2);

    // Subsequent rerenders with isHealthy: true do NOT trigger duplicate refetches
    rerender({ isHealthy: true });
    expect(getCurriculumMock).toHaveBeenCalledTimes(2);
  });

  it('recovers automatically when health becomes healthy while initial request is still in-flight and initial request subsequently fails', async () => {
    let rejectInitial: (err: unknown) => void;
    const initialPromise = new Promise((_, reject) => {
      rejectInitial = reject;
    });

    const getCurriculumMock = vi.fn()
      .mockImplementationOnce(() => initialPromise)
      .mockResolvedValueOnce(mockCurriculumResponse);

    const mockClient = {
      getCurriculum: getCurriculumMock,
    } as unknown as ApiClient;

    const { result, rerender } = renderHook(
      ({ isHealthy }) => useCurriculum(mockClient, isHealthy),
      { initialProps: { isHealthy: false } }
    );

    // Initial request in-flight
    expect(result.current.isLoading).toBe(true);
    expect(getCurriculumMock).toHaveBeenCalledTimes(1);

    // Health transitions to healthy while initial request is still pending
    rerender({ isHealthy: true });
    expect(result.current.isLoading).toBe(true);
    expect(getCurriculumMock).toHaveBeenCalledTimes(1);

    // Initial request fails
    await act(async () => {
      rejectInitial(ApiError.timeout(12000));
      await Promise.resolve();
    });

    // Hook automatically retries once after health is healthy
    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(getCurriculumMock).toHaveBeenCalledTimes(2);
    expect(result.current.error).toBeNull();
    expect(result.current.topics).toEqual(mockTopics);

    // Subsequent rerenders do not trigger duplicate calls
    rerender({ isHealthy: true });
    expect(getCurriculumMock).toHaveBeenCalledTimes(2);
  });

  it('does not enter infinite retry loop if recovery attempt also fails', async () => {
    const error1 = ApiError.timeout(12000);
    const error2 = new ApiError(500, {
      error: 'INTERNAL_SERVER_ERROR',
      message: 'Database still unavailable',
      retryable: true,
      timestamp: Date.now(),
    });

    const getCurriculumMock = vi.fn()
      .mockRejectedValueOnce(error1)
      .mockRejectedValueOnce(error2);

    const mockClient = {
      getCurriculum: getCurriculumMock,
    } as unknown as ApiClient;

    const { result, rerender } = renderHook(
      ({ isHealthy }) => useCurriculum(mockClient, isHealthy),
      { initialProps: { isHealthy: false } }
    );

    // Wait for initial failure
    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });
    expect(getCurriculumMock).toHaveBeenCalledTimes(1);
    expect(result.current.error).toBe(error1);

    // Transition to healthy triggers 1 recovery attempt
    rerender({ isHealthy: true });

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    // Recovery attempt failed with error2, stops retrying
    expect(getCurriculumMock).toHaveBeenCalledTimes(2);
    expect(result.current.error).toBe(error2);

    // Rerender with isHealthy: true does not trigger any further retries
    rerender({ isHealthy: true });
    expect(getCurriculumMock).toHaveBeenCalledTimes(2);
  });
});
