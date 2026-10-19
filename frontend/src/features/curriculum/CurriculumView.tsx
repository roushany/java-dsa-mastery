import React, { useState } from 'react';
import { useCurriculum } from '../../hooks/useCurriculum';
import { ApiClient, defaultApiClient } from '../../api/client';
import { ApiError } from '../../api/errors';
import { HealthResponse, SessionResponse, TopicSummaryDto } from '../../api/types';
import { saveSession } from '../../storage/sessionIndexStorage';

interface CurriculumViewProps {
  client?: ApiClient;
  health?: HealthResponse | null;
  onSessionStarted?: (session: SessionResponse) => void;
}

export const CurriculumView: React.FC<CurriculumViewProps> = ({
  client = defaultApiClient,
  health,
  onSessionStarted,
}) => {
  const isHealthy = health?.status === 'UP';
  const { topics, isLoading, error, refetch } = useCurriculum(client, isHealthy);
  const [startingTopicId, setStartingTopicId] = useState<string | null>(null);
  const [startError, setStartError] = useState<ApiError | null>(null);

  const handleStartSession = async (topicId: string) => {
    if (startingTopicId) {
      return; // Prevent duplicate concurrent submissions
    }

    setStartingTopicId(topicId);
    setStartError(null);

    try {
      const session = await client.startSession({ topicId });

      // Save valid session pointer locally for resume capability
      saveSession({
        sessionId: session.sessionId,
        topicId: session.topicId,
        createdAt: Date.now(),
        updatedAt: Date.now(),
        lastKnownState: session.currentState,
        lastKnownMastery: session.mastery,
      });

      if (onSessionStarted) {
        onSessionStarted(session);
      }
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setStartError(err);
      } else {
        setStartError(ApiError.networkError(err instanceof Error ? err : undefined));
      }
    } finally {
      setStartingTopicId(null);
    }
  };

  const getDifficultyBadge = (difficulty: string) => {
    const diff = difficulty.toUpperCase();
    switch (diff) {
      case 'BEGINNER':
        return 'bg-emerald-950/60 text-emerald-300 border-emerald-800/60';
      case 'INTERMEDIATE':
        return 'bg-sky-950/60 text-sky-300 border-sky-800/60';
      case 'ADVANCED':
        return 'bg-amber-950/60 text-amber-300 border-amber-800/60';
      default:
        return 'bg-slate-800 text-slate-300 border-slate-700';
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-50 tracking-tight">Curriculum Roadmap</h1>
        <p className="text-slate-400 mt-1">
          Explore structured Data Structures and Algorithms topics. Select any topic to begin an interactive session.
        </p>
      </div>

      {/* Start Session Error Alert */}
      {startError && (
        <div
          className="p-4 rounded-lg bg-red-950/50 border border-red-800/60 text-red-200 text-sm flex items-start justify-between gap-4"
          role="alert"
          data-testid="start-session-error"
        >
          <div>
            <div className="font-semibold text-red-100 flex items-center gap-1.5">
              <span>⚠</span> Failed to start session
            </div>
            <p className="mt-1">
              {startError.message}
              {startError.retryAfterSeconds && (
                <span className="block mt-1 font-mono text-xs text-red-300">
                  Rate limit reached. Please wait {startError.retryAfterSeconds}s before retrying.
                </span>
              )}
            </p>
          </div>
          <button
            onClick={() => setStartError(null)}
            className="text-red-400 hover:text-red-200 text-xs font-mono underline"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Loading State */}
      {isLoading && (
        <div
          className="p-12 text-center text-slate-400 space-y-3"
          data-testid="curriculum-loading"
        >
          <div className="w-8 h-8 mx-auto border-2 border-sky-500 border-t-transparent rounded-full animate-spin" />
          <p className="text-sm font-medium">Loading curriculum from backend...</p>
        </div>
      )}

      {/* Error State */}
      {!isLoading && error && (
        <div
          className="p-6 rounded-lg bg-red-950/30 border border-red-800/60 text-center space-y-4"
          data-testid="curriculum-error"
        >
          <div>
            <h3 className="text-base font-semibold text-red-200">Unable to load curriculum</h3>
            <p className="text-sm text-red-300/80 mt-1">{error.message}</p>
          </div>
          <button
            onClick={refetch}
            className="px-4 py-2 rounded-md bg-red-800 hover:bg-red-700 text-sm font-medium text-white transition-colors"
          >
            Retry Loading
          </button>
        </div>
      )}

      {/* Empty State */}
      {!isLoading && !error && topics.length === 0 && (
        <div
          className="p-12 rounded-lg bg-slate-800/40 border border-slate-700/60 text-center text-slate-400 text-sm"
          data-testid="curriculum-empty"
        >
          No curriculum topics are currently available from the backend.
        </div>
      )}

      {/* Topics Grid */}
      {!isLoading && !error && topics.length > 0 && (
        <div
          className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6"
          data-testid="topics-grid"
        >
          {topics.map((topic: TopicSummaryDto) => {
            const isStarting = startingTopicId === topic.topicId;
            const isAnyStarting = startingTopicId !== null;

            return (
              <div
                key={topic.topicId}
                className="bg-slate-800/80 border border-slate-700/80 rounded-lg p-5 flex flex-col justify-between hover:border-slate-600 transition-colors"
                data-testid={`topic-card-${topic.topicId}`}
              >
                <div>
                  {/* Header: Title and Difficulty */}
                  <div className="flex items-start justify-between gap-3">
                    <h2 className="text-lg font-semibold text-slate-100 tracking-tight">
                      {topic.title}
                    </h2>
                    <span
                      className={`text-xs font-mono font-medium px-2 py-0.5 rounded-full border ${getDifficultyBadge(
                        topic.difficulty
                      )}`}
                    >
                      {topic.difficulty}
                    </span>
                  </div>

                  {/* Metadata: Prerequisites & Stages */}
                  <div className="mt-4 space-y-2 text-xs text-slate-400">
                    <div>
                      <span className="text-slate-500 font-medium">Prerequisites: </span>
                      {topic.prerequisites && topic.prerequisites.length > 0 ? (
                        <span className="font-mono text-slate-300">
                          {topic.prerequisites.join(', ')}
                        </span>
                      ) : (
                        <span className="text-slate-500 italic">None (Foundational)</span>
                      )}
                    </div>

                    <div>
                      <span className="text-slate-500 font-medium">Curriculum Stages: </span>
                      <span className="font-mono text-slate-300">
                        {topic.totalStages > 0 ? `${topic.totalStages} stages` : 'Standard'}
                      </span>
                    </div>

                    {topic.hasContent && (
                      <div className="text-emerald-400/80 flex items-center gap-1 font-mono text-[11px]">
                        <span>✓</span> Verified Content
                      </div>
                    )}
                  </div>
                </div>

                {/* Action Button */}
                <div className="mt-6 pt-4 border-t border-slate-700/60">
                  <button
                    onClick={() => handleStartSession(topic.topicId)}
                    disabled={isAnyStarting}
                    aria-label={`Start session for ${topic.title}`}
                    className={`w-full py-2 px-4 rounded-md text-sm font-medium transition-colors flex items-center justify-center gap-2 ${
                      isStarting
                        ? 'bg-sky-600/50 text-white cursor-wait'
                        : isAnyStarting
                        ? 'bg-slate-700 text-slate-400 cursor-not-allowed'
                        : 'bg-sky-600 hover:bg-sky-500 text-white shadow-sm'
                    }`}
                  >
                    {isStarting ? (
                      <>
                        <span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                        Starting Session...
                      </>
                    ) : (
                      'Start Session'
                    )}
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
