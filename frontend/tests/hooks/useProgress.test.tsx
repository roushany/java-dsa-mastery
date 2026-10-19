import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { useProgress } from '../../src/hooks/useProgress';
import { ApiClient } from '../../src/api/client';
import { CurriculumResponse, SessionResponse } from '../../src/api/types';
import { ApiError } from '../../src/api/errors';
import {
  saveSession,
  getStoredSessions,
} from '../../src/storage/sessionIndexStorage';

describe('useProgress hook', () => {
  const sampleCurriculum: CurriculumResponse = {
    topics: [
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
        difficulty: 'BEGINNER',
        totalStages: 9,
        prerequisites: ['linear-search'],
        hasContent: true,
      },
    ],
  };

  const sampleVerifiedSession: SessionResponse = {
    sessionId: '11111111-2222-3333-4444-555555555555',
    learnerId: 'learner-alice',
    topicId: 'linear-search',
    topicTitle: 'Linear Search',
    currentState: 'DERIVE',
    mastery: 'FAMILIAR',
    totalStages: 1,
    completed: false,
  };

  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('handles empty local session storage cleanly without fabricating progress', async () => {
    const mockClient = {
      getCurriculum: vi.fn().mockResolvedValue(sampleCurriculum),
      getSession: vi.fn(),
    } as unknown as ApiClient;

    const { result } = renderHook(() => useProgress(mockClient));

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.sessionItems).toEqual([]);
    expect(result.current.curriculumTopics).toHaveLength(2);
    expect(mockClient.getSession).not.toHaveBeenCalled();
  });

  it('verifies stored session pointers against backend and synchronizes local state', async () => {
    saveSession({
      sessionId: '11111111-2222-3333-4444-555555555555',
      topicId: 'linear-search',
      createdAt: 1000,
      updatedAt: 1000,
      lastKnownState: 'CONCEPT_ASSESSMENT',
      lastKnownMastery: 'NOVICE',
    });

    const mockClient = {
      getCurriculum: vi.fn().mockResolvedValue(sampleCurriculum),
      getSession: vi.fn().mockResolvedValue(sampleVerifiedSession),
    } as unknown as ApiClient;

    const { result } = renderHook(() => useProgress(mockClient));

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(mockClient.getSession).toHaveBeenCalledWith(
      '11111111-2222-3333-4444-555555555555'
    );
    expect(result.current.sessionItems).toHaveLength(1);

    const verifiedItem = result.current.sessionItems[0];
    expect(verifiedItem.status).toBe('verified');
    expect(verifiedItem.session).toEqual(sampleVerifiedSession);
    expect(verifiedItem.session?.currentState).toBe('DERIVE');
    expect(verifiedItem.session?.mastery).toBe('FAMILIAR');

    // Local storage pointer synchronized with authoritative backend response
    const stored = getStoredSessions();
    expect(stored[0].lastKnownState).toBe('DERIVE');
    expect(stored[0].lastKnownMastery).toBe('FAMILIAR');
  });

  it('marks sessions as expired when server returns 404 or 410', async () => {
    saveSession({
      sessionId: '22222222-3333-4444-5555-666666666666',
      topicId: 'binary-search',
      createdAt: 1000,
      updatedAt: 1000,
    });

    const expiredError = new ApiError(410, {
      error: 'SESSION_EXPIRED',
      message: 'Session retention expired',
      retryable: false,
      timestamp: Date.now(),
    });

    const mockClient = {
      getCurriculum: vi.fn().mockResolvedValue(sampleCurriculum),
      getSession: vi.fn().mockRejectedValue(expiredError),
    } as unknown as ApiClient;

    const { result } = renderHook(() => useProgress(mockClient));

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.sessionItems).toHaveLength(1);
    const expiredItem = result.current.sessionItems[0];
    expect(expiredItem.status).toBe('expired');
    expect(expiredItem.error).toBe(expiredError);

    // Can prune expired pointer safely
    act(() => {
      result.current.removeExpiredSession('22222222-3333-4444-5555-666666666666');
    });

    expect(result.current.sessionItems).toHaveLength(0);
    expect(getStoredSessions()).toHaveLength(0);
  });

  it('marks sessions as unauthorized when server returns 401 or 403', async () => {
    saveSession({
      sessionId: '33333333-4444-5555-6666-777777777777',
      topicId: 'binary-search',
      createdAt: 1000,
      updatedAt: 1000,
    });

    const forbiddenError = new ApiError(403, {
      error: 'FORBIDDEN_ACCESS',
      message: 'Cross-learner access denied',
      retryable: false,
      timestamp: Date.now(),
    });

    const mockClient = {
      getCurriculum: vi.fn().mockResolvedValue(sampleCurriculum),
      getSession: vi.fn().mockRejectedValue(forbiddenError),
    } as unknown as ApiClient;

    const { result } = renderHook(() => useProgress(mockClient));

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.sessionItems).toHaveLength(1);
    const unauthorizedItem = result.current.sessionItems[0];
    expect(unauthorizedItem.status).toBe('unauthorized');
    expect(unauthorizedItem.error).toBe(forbiddenError);
  });

  it('keeps local session pointer intact on transient network error without purging', async () => {
    saveSession({
      sessionId: '44444444-5555-6666-7777-888888888888',
      topicId: 'linear-search',
      createdAt: 1000,
      updatedAt: 1000,
    });

    const networkError = ApiError.networkError(new Error('Connection refused'));

    const mockClient = {
      getCurriculum: vi.fn().mockResolvedValue(sampleCurriculum),
      getSession: vi.fn().mockRejectedValue(networkError),
    } as unknown as ApiClient;

    const { result } = renderHook(() => useProgress(mockClient));

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.sessionItems).toHaveLength(1);
    const item = result.current.sessionItems[0];
    expect(item.status).toBe('network_error');
    expect(item.error?.message).toContain('Connection refused');

    // Crucial: Pointer is NOT purged on network error!
    expect(getStoredSessions()).toHaveLength(1);
  });

  it('recovers gracefully from corrupted local session index', async () => {
    localStorage.setItem('javadsa_session_index', '{"not": "an array"}');

    const mockClient = {
      getCurriculum: vi.fn().mockResolvedValue(sampleCurriculum),
      getSession: vi.fn(),
    } as unknown as ApiClient;

    const { result } = renderHook(() => useProgress(mockClient));

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    // Should not crash, returns empty session list
    expect(result.current.sessionItems).toEqual([]);
    expect(getStoredSessions()).toEqual([]);
  });

  it('re-verifies sessions when refreshProgress is invoked', async () => {
    saveSession({
      sessionId: '11111111-2222-3333-4444-555555555555',
      topicId: 'linear-search',
      createdAt: 1000,
      updatedAt: 1000,
    });

    const mockClient = {
      getCurriculum: vi.fn().mockResolvedValue(sampleCurriculum),
      getSession: vi
        .fn()
        .mockResolvedValueOnce(sampleVerifiedSession)
        .mockResolvedValueOnce({
          ...sampleVerifiedSession,
          currentState: 'COMPLETED',
          completed: true,
          mastery: 'MASTERED',
        }),
    } as unknown as ApiClient;

    const { result } = renderHook(() => useProgress(mockClient));

    await waitFor(() => {
      expect(result.current.sessionItems[0]?.status).toBe('verified');
      expect(result.current.sessionItems[0]?.session?.currentState).toBe('DERIVE');
    });

    // Trigger re-verification
    await act(async () => {
      await result.current.refreshProgress();
    });

    expect(result.current.sessionItems[0]?.session?.currentState).toBe('COMPLETED');
    expect(result.current.sessionItems[0]?.session?.completed).toBe(true);
    expect(result.current.sessionItems[0]?.session?.mastery).toBe('MASTERED');
  });
});
