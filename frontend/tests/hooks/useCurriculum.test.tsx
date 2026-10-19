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
});
