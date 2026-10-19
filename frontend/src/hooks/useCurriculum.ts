import { useState, useEffect, useCallback } from 'react';
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
 */
export function useCurriculum(client: ApiClient = defaultApiClient): UseCurriculumResult {
  const [topics, setTopics] = useState<TopicSummaryDto[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<ApiError | null>(null);

  const fetchCurriculum = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const response = await client.getCurriculum();
      setTopics(Array.isArray(response?.topics) ? response.topics : []);
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError(err);
      } else {
        setError(ApiError.networkError(err instanceof Error ? err : undefined));
      }
      setTopics([]);
    } finally {
      setIsLoading(false);
    }
  }, [client]);

  useEffect(() => {
    fetchCurriculum();
  }, [fetchCurriculum]);

  return { topics, isLoading, error, refetch: fetchCurriculum };
}
