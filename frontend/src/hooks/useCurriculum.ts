import { useState, useEffect, useCallback, useRef } from 'react';
import { TopicSummaryDto } from '../api/types';
import { ApiError } from '../api/errors';
import { ApiClient, defaultApiClient } from '../api/client';

export interface UseCurriculumResult {
  topics: TopicSummaryDto[];
  isLoading: boolean;
  error: ApiError | null;
  refetch: () => void;
}

/**
 * React hook to fetch and manage curriculum topics from GET /api/v1/curriculum.
 * Never hardcodes topics; dynamically parses backend response.
 * Recovers automatically if initial load failed during backend startup and backend becomes healthy.
 */
export function useCurriculum(
  client: ApiClient = defaultApiClient,
  isHealthy: boolean = false
): UseCurriculumResult {
  const [topics, setTopics] = useState<TopicSummaryDto[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<ApiError | null>(null);

  const requestSeqRef = useRef<number>(0);
  const isMountedRef = useRef<boolean>(true);
  const hasRecoveredForCurrentHealthCycleRef = useRef<boolean>(false);

  const fetchCurriculum = useCallback(async () => {
    const seq = ++requestSeqRef.current;
    setIsLoading(true);
    setError(null);

    try {
      const response = await client.getCurriculum();
      if (!isMountedRef.current || seq !== requestSeqRef.current) return;

      setTopics(Array.isArray(response?.topics) ? response.topics : []);
      setError(null);
    } catch (err: unknown) {
      if (!isMountedRef.current || seq !== requestSeqRef.current) return;

      if (err instanceof ApiError) {
        setError(err);
      } else {
        setError(ApiError.networkError(err instanceof Error ? err : undefined));
      }
      setTopics([]);
    } finally {
      if (isMountedRef.current && seq === requestSeqRef.current) {
        setIsLoading(false);
      }
    }
  }, [client]);

  const handleManualRefetch = useCallback(() => {
    hasRecoveredForCurrentHealthCycleRef.current = false;
    fetchCurriculum();
  }, [fetchCurriculum]);

  // Initial fetch on mount
  useEffect(() => {
    isMountedRef.current = true;
    fetchCurriculum();

    return () => {
      isMountedRef.current = false;
    };
  }, [fetchCurriculum]);

  // Automatic recovery when backend is healthy after initial failure
  // Retries at most once per healthy cycle to avoid duplicate requests and infinite loops
  useEffect(() => {
    if (!isHealthy) {
      hasRecoveredForCurrentHealthCycleRef.current = false;
      return;
    }

    if (
      isHealthy &&
      !hasRecoveredForCurrentHealthCycleRef.current &&
      error !== null &&
      topics.length === 0 &&
      !isLoading
    ) {
      hasRecoveredForCurrentHealthCycleRef.current = true;
      fetchCurriculum();
    }
  }, [isHealthy, error, topics.length, isLoading, fetchCurriculum]);

  return { topics, isLoading, error, refetch: handleManualRefetch };
}
