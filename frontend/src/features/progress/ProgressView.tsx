import React from 'react';
import { ApiClient, defaultApiClient } from '../../api/client';
import { SessionResponse } from '../../api/types';
import { useProgress } from '../../hooks/useProgress';
import { getStoredToken, getLearnerIdFromToken } from '../../storage/tokenStorage';

interface ProgressViewProps {
  client?: ApiClient;
  onNavigateToCurriculum: () => void;
  onResumeSession: (session: SessionResponse) => void;
}

export const ProgressView: React.FC<ProgressViewProps> = ({
  client = defaultApiClient,
  onNavigateToCurriculum,
  onResumeSession,
}) => {
  const {
    sessionItems,
    curriculumTopics,
    isLoading,
    curriculumError,
    refreshProgress,
    removeExpiredSession,
  } = useProgress(client);

  const learnerId = getLearnerIdFromToken(getStoredToken());

  const verifiedActiveSessions = sessionItems.filter(
    (item) => item.status === 'verified' && !item.session?.completed
  );
  const completedSessions = sessionItems.filter(
    (item) => item.status === 'verified' && item.session?.completed
  );
  const unverifiedOrStaleCount = sessionItems.filter(
    (item) => item.status === 'expired' || item.status === 'unauthorized' || item.status === 'network_error'
  ).length;

  const getMasteryBadge = (mastery: string | undefined) => {
    switch (mastery?.toUpperCase()) {
      case 'MASTERED':
        return 'bg-purple-950/60 text-purple-300 border-purple-800/60';
      case 'PROFICIENT':
        return 'bg-emerald-950/60 text-emerald-300 border-emerald-800/60';
      case 'FAMILIAR':
        return 'bg-sky-950/60 text-sky-300 border-sky-800/60';
      case 'NOVICE':
      default:
        return 'bg-amber-950/60 text-amber-300 border-amber-800/60';
    }
  };

  const formatStage = (stage: string | undefined) => {
    if (!stage) return 'Not Started';
    return stage
      .replace(/_/g, ' ')
      .toLowerCase()
      .replace(/\b\w/g, (c) => c.toUpperCase());
  };

  const formatDate = (timestamp: number) => {
    try {
      return new Date(timestamp).toLocaleDateString(undefined, {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return 'Unknown date';
    }
  };

  return (
    <div className="space-y-8" data-testid="progress-view">
      {/* Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 pb-2 border-b border-slate-800">
        <div>
          <h1 className="text-2xl font-bold text-slate-50 tracking-tight">
            Learner Progress &amp; Mastery
          </h1>
          <p className="text-slate-400 text-sm mt-1">
            Evidence-backed mastery tracking verified independently against the backend server.
          </p>
        </div>

        <button
          onClick={() => refreshProgress()}
          disabled={isLoading}
          className="px-3.5 py-1.5 rounded-md bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-medium border border-slate-700 transition-colors flex items-center gap-2"
          data-testid="btn-refresh-progress"
        >
          {isLoading ? (
            <>
              <span className="w-3 h-3 border-2 border-sky-400 border-t-transparent rounded-full animate-spin" />
              Verifying...
            </>
          ) : (
            'Re-verify Sessions'
          )}
        </button>
      </div>

      {/* Honest Scope Disclosure Banner */}
      <div
        className="p-4 rounded-lg bg-sky-950/30 border border-sky-800/50 text-sm text-sky-200 space-y-1.5"
        role="region"
        aria-label="Scope Transparency"
        data-testid="progress-scope-notice"
      >
        <div className="flex items-center gap-2 font-semibold text-sky-100">
          <span>ℹ</span> Browser-Indexed Session Scope
        </div>
        <p className="text-xs leading-relaxed text-sky-200/90">
          This view reflects learning sessions saved on this browser, verified live against the Spring Boot server. The backend architecture does not maintain a central cross-device account history endpoint. Mastery standings are displayed only when authoritatively confirmed by the server for each session.
        </p>
      </div>

      {/* Identity & Verification Metrics Overview */}
      <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
        <div className="p-4 rounded-lg bg-slate-800/60 border border-slate-700">
          <div className="text-xs text-slate-400 font-mono">Learner Identity</div>
          <div className="text-sm font-semibold text-slate-100 mt-1 truncate" title={learnerId ?? 'Anonymous'}>
            {learnerId ?? 'Anonymous / Unlinked'}
          </div>
        </div>

        <div className="p-4 rounded-lg bg-slate-800/60 border border-slate-700">
          <div className="text-xs text-slate-400 font-mono">Verified Active Sessions</div>
          <div className="text-2xl font-bold text-sky-400 mt-1">
            {verifiedActiveSessions.length}
          </div>
        </div>

        <div className="p-4 rounded-lg bg-slate-800/60 border border-slate-700">
          <div className="text-xs text-slate-400 font-mono">Topics Completed</div>
          <div className="text-2xl font-bold text-emerald-400 mt-1">
            {completedSessions.length}
          </div>
        </div>

        <div className="p-4 rounded-lg bg-slate-800/60 border border-slate-700">
          <div className="text-xs text-slate-400 font-mono">Stale / Unreachable</div>
          <div className="text-2xl font-bold text-amber-400 mt-1">
            {unverifiedOrStaleCount}
          </div>
        </div>
      </div>

      {/* Curriculum Network Error Banner if any */}
      {curriculumError && (
        <div
          className="p-4 rounded-lg bg-red-950/40 border border-red-800/60 text-sm text-red-200"
          role="alert"
        >
          <div className="font-semibold text-red-100 flex items-center gap-1.5">
            <span>⚠</span> Curriculum Fetch Notice
          </div>
          <p className="text-xs text-red-300 mt-1">
            {curriculumError.message}. Topic metadata may be incomplete until the backend connection is restored.
          </p>
        </div>
      )}

      {/* Verified Sessions Section */}
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold text-slate-100">
            Verified Browser Sessions ({sessionItems.length})
          </h2>
        </div>

        {sessionItems.length === 0 ? (
          <div
            className="p-10 rounded-lg bg-slate-800/30 border border-slate-700/60 text-center space-y-4"
            data-testid="empty-progress-state"
          >
            <div className="w-10 h-10 mx-auto rounded-full bg-slate-800 flex items-center justify-center text-slate-400">
              📋
            </div>
            <div className="space-y-1">
              <h3 className="font-medium text-slate-200">No learning sessions saved in this browser</h3>
              <p className="text-xs text-slate-400 max-w-md mx-auto">
                Start a topic from the curriculum roadmap to begin generating evidence-backed progress.
              </p>
            </div>
            <button
              onClick={onNavigateToCurriculum}
              className="px-4 py-2 rounded-md bg-sky-600 hover:bg-sky-500 text-white text-xs font-medium transition-colors"
            >
              Browse Curriculum
            </button>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4" data-testid="verified-sessions-list">
            {sessionItems.map((item) => {
              const matchedTopic = curriculumTopics.find((t) => t.topicId === item.topicId);
              const title = item.session?.topicTitle ?? matchedTopic?.title ?? item.topicId;

              if (item.status === 'verifying') {
                return (
                  <div
                    key={item.sessionId}
                    className="p-5 rounded-lg bg-slate-800/40 border border-slate-700 space-y-3 animate-pulse"
                    data-testid={`session-verifying-${item.sessionId}`}
                  >
                    <div className="h-4 bg-slate-700 rounded w-1/2" />
                    <div className="h-3 bg-slate-700/60 rounded w-3/4" />
                    <div className="text-xs text-slate-400">Verifying session with server...</div>
                  </div>
                );
              }

              if (item.status === 'expired') {
                return (
                  <div
                    key={item.sessionId}
                    className="p-5 rounded-lg bg-amber-950/20 border border-amber-800/50 space-y-3"
                    data-testid={`session-expired-${item.sessionId}`}
                  >
                    <div className="flex items-center justify-between">
                      <h3 className="font-semibold text-slate-100 text-sm">{title}</h3>
                      <span className="text-xs font-mono px-2 py-0.5 rounded-full border bg-amber-950/60 text-amber-300 border-amber-800/60">
                        Expired / Purged
                      </span>
                    </div>
                    <p className="text-xs text-amber-300/80">
                      The server retention window for this session has elapsed (HTTP {item.error?.status ?? 410}).
                    </p>
                    <div className="flex items-center justify-between pt-2 border-t border-amber-900/40">
                      <span className="text-xs font-mono text-slate-500">ID: {item.sessionId.slice(0, 8)}...</span>
                      <button
                        onClick={() => removeExpiredSession(item.sessionId)}
                        className="text-xs text-amber-400 hover:text-amber-300 font-medium transition-colors"
                        data-testid={`btn-remove-${item.sessionId}`}
                      >
                        Remove Stale Reference
                      </button>
                    </div>
                  </div>
                );
              }

              if (item.status === 'unauthorized') {
                return (
                  <div
                    key={item.sessionId}
                    className="p-5 rounded-lg bg-red-950/20 border border-red-800/50 space-y-3"
                    data-testid={`session-unauthorized-${item.sessionId}`}
                  >
                    <div className="flex items-center justify-between">
                      <h3 className="font-semibold text-slate-100 text-sm">{title}</h3>
                      <span className="text-xs font-mono px-2 py-0.5 rounded-full border bg-red-950/60 text-red-300 border-red-800/60">
                        Unauthorized
                      </span>
                    </div>
                    <p className="text-xs text-red-300/80">
                      This session was initiated by a different learner token than the one active in this browser.
                    </p>
                    <div className="flex items-center justify-between pt-2 border-t border-red-900/40">
                      <span className="text-xs font-mono text-slate-500">ID: {item.sessionId.slice(0, 8)}...</span>
                      <button
                        onClick={() => removeExpiredSession(item.sessionId)}
                        className="text-xs text-red-400 hover:text-red-300 font-medium transition-colors"
                        data-testid={`btn-remove-${item.sessionId}`}
                      >
                        Remove Stale Reference
                      </button>
                    </div>
                  </div>
                );
              }

              if (item.status === 'network_error') {
                return (
                  <div
                    key={item.sessionId}
                    className="p-5 rounded-lg bg-slate-800/50 border border-slate-700 space-y-3"
                    data-testid={`session-network-error-${item.sessionId}`}
                  >
                    <div className="flex items-center justify-between">
                      <h3 className="font-semibold text-slate-100 text-sm">{title}</h3>
                      <span className="text-xs font-mono px-2 py-0.5 rounded-full border bg-slate-800 text-slate-300 border-slate-700">
                        Temporarily Unreachable
                      </span>
                    </div>
                    <p className="text-xs text-slate-400">
                      Unable to verify with backend: {item.error?.message}. Your local pointer remains intact.
                    </p>
                    <div className="flex items-center justify-between pt-2 border-t border-slate-700/60">
                      <span className="text-xs font-mono text-slate-500">Last updated: {formatDate(item.updatedAt)}</span>
                      <button
                        onClick={() => refreshProgress()}
                        className="text-xs text-sky-400 hover:text-sky-300 font-medium transition-colors"
                      >
                        Retry Verification
                      </button>
                    </div>
                  </div>
                );
              }

              // Verified Session Item
              const session = item.session!;
              const isCompleted = session.completed;

              return (
                <div
                  key={item.sessionId}
                  className="p-5 rounded-lg bg-slate-800/70 border border-slate-700 space-y-3"
                  data-testid={`session-verified-${item.sessionId}`}
                >
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <h3 className="font-semibold text-slate-100 text-base">{title}</h3>
                      <div className="flex items-center gap-2 mt-1">
                        <span className="text-xs font-mono text-sky-400">
                          {isCompleted ? '✓ Completed' : `Stage: ${formatStage(session.currentState)}`}
                        </span>
                        <span className="text-xs text-slate-500">•</span>
                        <span className="text-xs font-mono text-slate-400">
                          Total stages: {session.totalStages}
                        </span>
                      </div>
                    </div>

                    <span
                      className={`text-xs font-mono px-2.5 py-0.5 rounded-full border ${getMasteryBadge(
                        session.mastery
                      )}`}
                    >
                      {session.mastery}
                    </span>
                  </div>

                  <div className="flex items-center justify-between pt-3 border-t border-slate-700/60">
                    <span className="text-xs font-mono text-slate-500">
                      Updated: {formatDate(item.updatedAt)}
                    </span>
                    <button
                      onClick={() => onResumeSession(session)}
                      className="px-3 py-1.5 rounded bg-sky-600 hover:bg-sky-500 text-white text-xs font-medium transition-colors"
                      data-testid={`btn-resume-${item.sessionId}`}
                    >
                      {isCompleted ? 'Review Topic' : 'Resume Session'}
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Curriculum Coverage Roadmap (Only Real Data) */}
      <div className="space-y-4">
        <h2 className="text-lg font-semibold text-slate-100">
          Curriculum Topic Coverage ({curriculumTopics.length} Topics)
        </h2>
        <div className="bg-slate-800/40 border border-slate-700 rounded-lg overflow-hidden">
          <div className="divide-y divide-slate-700/60">
            {curriculumTopics.map((topic) => {
              const matchedSessions = sessionItems.filter(
                (s) => s.status === 'verified' && s.topicId === topic.topicId
              );
              const isCompleted = matchedSessions.some((s) => s.session?.completed);
              const activeSession = matchedSessions.find((s) => !s.session?.completed);

              return (
                <div
                  key={topic.topicId}
                  className="p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3"
                  data-testid={`topic-row-${topic.topicId}`}
                >
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <span className="font-semibold text-sm text-slate-100">{topic.title}</span>
                      <span className="text-xs font-mono px-2 py-0.5 rounded bg-slate-800 text-slate-400 border border-slate-700">
                        {topic.difficulty}
                      </span>
                    </div>
                    {topic.prerequisites.length > 0 && (
                      <p className="text-xs text-slate-400">
                        Prerequisites: {topic.prerequisites.join(', ')}
                      </p>
                    )}
                  </div>

                  <div>
                    {isCompleted ? (
                      <span className="text-xs font-mono px-2.5 py-1 rounded-full border bg-emerald-950/60 text-emerald-300 border-emerald-800/60">
                        ✓ Topic Completed
                      </span>
                    ) : activeSession ? (
                      <span className="text-xs font-mono px-2.5 py-1 rounded-full border bg-sky-950/60 text-sky-300 border-sky-800/60">
                        In Progress ({formatStage(activeSession.session?.currentState)})
                      </span>
                    ) : (
                      <span className="text-xs font-mono px-2.5 py-1 rounded-full border bg-slate-800 text-slate-400 border-slate-700">
                        Not Started on this Browser
                      </span>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>

      {/* Mastery Tier Pedagogical Definitions */}
      <div className="p-6 rounded-lg bg-slate-800/40 border border-slate-700 space-y-3 text-xs text-slate-300">
        <h3 className="text-sm font-semibold text-slate-200">Mastery Criteria Explanation</h3>
        <p className="text-slate-400">
          The platform evaluates mastery dynamically through sequential evidence records rather than simple completion percentages:
        </p>
        <ul className="grid grid-cols-1 md:grid-cols-2 gap-3 pt-1">
          <li className="p-3 rounded bg-slate-900/60 border border-slate-800">
            <span className="font-semibold text-amber-300">NOVICE:</span> Initial exploration and derivation. The learner is establishing foundational mental models.
          </li>
          <li className="p-3 rounded bg-slate-900/60 border border-slate-800">
            <span className="font-semibold text-sky-300">FAMILIAR:</span> Derives loop invariants and complexity bounds with declining hint reliance.
          </li>
          <li className="p-3 rounded bg-slate-900/60 border border-slate-800">
            <span className="font-semibold text-emerald-300">PROFICIENT:</span> Analyzes implementations, handles edge cases, and debugs subtle off-by-one errors.
          </li>
          <li className="p-3 rounded bg-slate-900/60 border border-slate-800">
            <span className="font-semibold text-purple-300">MASTERED:</span> Transfers algorithmic patterns to novel variants without scaffolding.
          </li>
        </ul>
      </div>
    </div>
  );
};
