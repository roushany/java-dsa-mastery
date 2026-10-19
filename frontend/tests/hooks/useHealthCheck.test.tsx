import { describe, it, expect, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { useHealthCheck } from '../../src/hooks/useHealthCheck';
import { ApiClient } from '../../src/api/client';
import { HealthResponse } from '../../src/api/types';
import { ApiError } from '../../src/api/errors';

describe('useHealthCheck', () => {
  it('loads health data successfully', async () => {
    const mockHealth: HealthResponse = {
      status: 'UP',
      version: '1.0.0',
      activeTopics: 7,
      aiConfigured: true,
    };

    const mockClient = {
      getHealth: vi.fn().mockResolvedValue(mockHealth),
    } as unknown as ApiClient;

    const { result } = renderHook(() => useHealthCheck(mockClient));

    expect(result.current.isLoading).toBe(true);

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.health).toEqual(mockHealth);
    expect(result.current.error).toBeNull();
  });

  it('handles health check error cleanly', async () => {
    const mockError = new ApiError(503, {
      error: 'PROVIDER_UNAVAILABLE',
      message: 'Service down',
      retryable: true,
      timestamp: Date.now(),
    });

    const mockClient = {
      getHealth: vi.fn().mockRejectedValue(mockError),
    } as unknown as ApiClient;

    const { result } = renderHook(() => useHealthCheck(mockClient));

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.health).toBeNull();
    expect(result.current.error).toBe(mockError);
  });
});
