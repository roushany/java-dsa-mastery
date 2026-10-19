import { useState, useEffect, useCallback } from 'react';
import { HealthResponse } from '../api/types';
import { ApiError } from '../api/errors';
import { ApiClient, defaultApiClient } from '../api/client';

export interface UseHealthCheckResult {
  health: HealthResponse | null;
  isLoading: boolean;
  error: ApiError | null;
  refetch: () => void;
}

export function useHealthCheck(client: ApiClient = defaultApiClient): UseHealthCheckResult {
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<ApiError | null>(null);

  const checkHealth = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await client.getHealth();
      setHealth(data);
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError(err);
      } else {
        setError(ApiError.networkError(err instanceof Error ? err : undefined));
      }
      setHealth(null);
    } finally {
      setIsLoading(false);
    }
  }, [client]);

  useEffect(() => {
    checkHealth();
  }, [checkHealth]);

  return { health, isLoading, error, refetch: checkHealth };
}
