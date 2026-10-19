import { useState, useEffect, useCallback, useRef } from 'react';
import { ApiClient, defaultApiClient } from '../api/client';
import { ApiError } from '../api/errors';
import { SessionResponse, TopicSummaryDto } from '../api/types';
import {
  getStoredSessions,
  removeSession,
  saveSession,
} from '../storage/sessionIndexStorage';

export type VerifiedSessionStatus =
  | 'verifying'
  | 'verified'
  | 'expired'
  | 'unauthorized'
  | 'network_error';

export interface VerifiedSessionItem {
  sessionId: string;
  topicId: string;
  createdAt: number;
  updatedAt: number;
  status: VerifiedSessionStatus;
  session?: SessionResponse;
  error?: ApiError;
}

export interface UseProgressResult {
  sessionItems: VerifiedSessionItem[];
  curriculumTopics: TopicSummaryDto[];
  isLoading: boolean;
  curriculumError: ApiError | null;
  refreshProgress: () => Promise<void>;
  removeExpiredSession: (sessionId: string) => void;
}

/**
 * Hook to independently verify browser-stored learning session pointers against
 * the backend Spring Boot server, preventing progress fabrication.
 */
export function useProgress(client: ApiClient = defaultApiClient): UseProgressResult {
  const [sessionItems, setSessionItems] = useState<VerifiedSessionItem[]>([]);
  const [curriculumTopics, setCurriculumTopics] = useState<TopicSummaryDto[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [curriculumError, setCurriculumError] = useState<ApiError | null>(null);

  const requestSeqRef = useRef<number>(0);

  const refreshProgress = useCallback(async () => {
    const seq = ++requestSeqRef.current;
    setIsLoading(true);
    setCurriculumError(null);

    // 1. Fetch curriculum roadmap for topic metadata
    try {
      const curriculumResp = await client.getCurriculum();
      if (seq === requestSeqRef.current) {
        setCurriculumTopics(curriculumResp.topics);
      }
    } catch (err: unknown) {
      if (seq === requestSeqRef.current) {
        const apiErr =
          err instanceof ApiError
            ? err
            : ApiError.networkError(err instanceof Error ? err : undefined);
        setCurriculumError(apiErr);
      }
    }

    // 2. Read stored session index references from browser storage
    const storedPointers = getStoredSessions();

    if (storedPointers.length === 0) {
      if (seq === requestSeqRef.current) {
        setSessionItems([]);
        setIsLoading(false);
      }
      return;
    }

    // Initialize with verifying status
    const initialItems: VerifiedSessionItem[] = storedPointers.map((p) => ({
      sessionId: p.sessionId,
      topicId: p.topicId,
      createdAt: p.createdAt,
      updatedAt: p.updatedAt,
      status: 'verifying',
    }));

    if (seq === requestSeqRef.current) {
      setSessionItems(initialItems);
    }

    // 3. Verify each session individually with the backend
    const verifiedResults = await Promise.all(
      storedPointers.map(async (pointer): Promise<VerifiedSessionItem> => {
        try {
          const verified = await client.getSession(pointer.sessionId);

          // Authoritatively confirmed by backend: sync local pointer
          saveSession({
            sessionId: verified.sessionId,
            topicId: verified.topicId,
            createdAt: pointer.createdAt,
            updatedAt: Date.now(),
            lastKnownState: verified.currentState,
            lastKnownMastery: verified.mastery,
          });

          return {
            sessionId: pointer.sessionId,
            topicId: verified.topicId,
            createdAt: pointer.createdAt,
            updatedAt: Date.now(),
            status: 'verified',
            session: verified,
          };
        } catch (err: unknown) {
          const apiErr =
            err instanceof ApiError
              ? err
              : ApiError.networkError(err instanceof Error ? err : undefined);

          if (apiErr.isNotFound || apiErr.isSessionExpired) {
            return {
              sessionId: pointer.sessionId,
              topicId: pointer.topicId,
              createdAt: pointer.createdAt,
              updatedAt: pointer.updatedAt,
              status: 'expired',
              error: apiErr,
            };
          }

          if (apiErr.isUnauthorized || apiErr.isForbidden) {
            return {
              sessionId: pointer.sessionId,
              topicId: pointer.topicId,
              createdAt: pointer.createdAt,
              updatedAt: pointer.updatedAt,
              status: 'unauthorized',
              error: apiErr,
            };
          }

          // Transient network or server error
          return {
            sessionId: pointer.sessionId,
            topicId: pointer.topicId,
            createdAt: pointer.createdAt,
            updatedAt: pointer.updatedAt,
            status: 'network_error',
            error: apiErr,
          };
        }
      })
    );

    if (seq === requestSeqRef.current) {
      setSessionItems(verifiedResults);
      setIsLoading(false);
    }
  }, [client]);

  useEffect(() => {
    refreshProgress();
  }, [refreshProgress]);

  const removeExpiredSession = useCallback((sessionId: string) => {
    removeSession(sessionId);
    setSessionItems((prev) => prev.filter((item) => item.sessionId !== sessionId));
  }, []);

  return {
    sessionItems,
    curriculumTopics,
    isLoading,
    curriculumError,
    refreshProgress,
    removeExpiredSession,
  };
}
