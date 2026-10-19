import React, { useState } from 'react';
import { ApiClient, defaultApiClient } from '../../api/client';
import { ApiError } from '../../api/errors';
import { HealthResponse, SessionResponse } from '../../api/types';
import {
  getStoredSessions,
  removeSession,
  saveSession,
  SessionIndexEntry,
} from '../../storage/sessionIndexStorage';
import { getStoredToken, getLearnerIdFromToken } from '../../storage/tokenStorage';

interface DashboardViewProps {
  client?: ApiClient;
  health: HealthResponse | null;
  isLoadingHealth: boolean;
  onNavigateToCurriculum: () => void;
  onResumeSession?: (session: SessionResponse) => void;
}

export const DashboardView: React.FC<DashboardViewProps> = ({
  client = defaultApiClient,
  health,
  isLoadingHealth,
  onNavigateToCurriculum,
  onResumeSession,
}) => {
  const [sessions, setSessions] = useState<SessionIndexEntry[]>(() => getStoredSessions());
  const [verifyingSessionId, setVerifyingSessionId] = useState<string | null>(null);
  const [resumeNotification, setResumeNotification] = useState<{
    type: 'error' | 'info';
    message: string;
  } | null>(null);

  const learnerId = getLearnerIdFromToken(getStoredToken());

  const handleResumeSession = async (candidate: SessionIndexEntry) => {
    if (verifyingSessionId) {
      return; // Prevent duplicate concurrent verification
    }

    setVerifyingSessionId(candidate.sessionId);
    setResumeNotification(null);

    try {
      // Confirm with backend before claiming session is resumable
      const verifiedSession = await client.getSession(candidate.sessionId);

      // Backend confirmed session is active and owned: update local pointer
      saveSession({
        sessionId: verifiedSession.sessionId,
        topicId: verifiedSession.topicId,
        createdAt: candidate.createdAt,
        updatedAt: Date.now(),
        lastKnownState: verifiedSession.currentState,
        lastKnownMastery: verifiedSession.mastery,
      });

      // Update state list
      setSessions(getStoredSessions());

      if (onResumeSession) {
        onResumeSession(verifiedSession);
      }
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        // If session was purged (404) or expired (410), prune local pointer safely
        if (err.isNotFound || err.isSessionExpired) {
          removeSession(candidate.sessionId);
          setSessions(getStoredSessions());
          setResumeNotification({
            type: 'info',
            message: `Session ${candidate.sessionId.substring(0, 8)}... has expired or was purged on the server (${err.errorCode}). Removed from your saved candidates.`,
          });
          return;
        }

        // For other errors (e.g. 403 cross-learner, network, 5xx), keep pointer and report
        setResumeNotification({
          type: 'error',
          message: `Unable to resume session: ${err.message}`,
        });
      } else {
        setResumeNotification({
          type: 'error',
          message: 'Unable to connect to server to verify session.',
        });
      }
    } finally {
      setVerifyingSessionId(null);
    }
  };

  const formatDate = (timestamp: number) => {
    try {
      return new Date(timestamp).toLocaleString(undefined, {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return 'Recently';
    }
  };

  return (
    <div className="space-y-8">
      {/* Hero Welcome */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-6 border-b border-slate-800">
        <div>
          <h1 className="text-2xl sm:text-3xl font-bold text-slate-50 tracking-tight">
            Dashboard
          </h1>
          <p className="text-slate-400 mt-1">
            Learn Java Data Structures and Algorithms with structured, evidence-backed mastery.
          </p>
        </div>
        <button
          onClick={onNavigateToCurriculum}
          className="px-5 py-2.5 rounded-lg bg-sky-600 hover:bg-sky-500 text-white font-medium text-sm transition-colors shadow-sm self-start sm:self-auto"
        >
          Browse Curriculum &rarr;
        </button>
      </div>

      {/* Resume Diagnostic Notification */}
      {resumeNotification && (
        <div
          className={`p-4 rounded-lg text-sm flex items-start justify-between gap-4 ${
            resumeNotification.type === 'error'
              ? 'bg-red-950/40 border border-red-800/60 text-red-200'
              : 'bg-amber-950/40 border border-amber-800/60 text-amber-200'
          }`}
          role="alert"
          data-testid="resume-notification"
        >
          <div>
            <div className="font-semibold flex items-center gap-1.5">
              <span>{resumeNotification.type === 'error' ? '⚠' : 'ℹ'}</span>
              <span>Session Status</span>
            </div>
            <p className="mt-1">{resumeNotification.message}</p>
          </div>
          <button
            onClick={() => setResumeNotification(null)}
            className="text-xs font-mono underline opacity-80 hover:opacity-100"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Candidate Resumable Sessions */}
      <section className="space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold text-slate-100 tracking-tight">
            Saved Session Candidates
          </h2>
          <span className="text-xs text-slate-500 font-mono">
            {sessions.length} saved locally
          </span>
        </div>

        {sessions.length === 0 ? (
          <div
            className="p-8 rounded-lg bg-slate-800/40 border border-slate-700/60 text-center space-y-3"
            data-testid="empty-sessions"
          >
            <p className="text-sm text-slate-400">
              No saved sessions found on this device.
            </p>
            <button
              onClick={onNavigateToCurriculum}
              className="text-sm text-sky-400 hover:text-sky-300 font-medium underline"
            >
              Select a topic from the curriculum to get started
            </button>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4" data-testid="session-candidates-grid">
            {sessions.map((candidate) => {
              const isVerifying = verifyingSessionId === candidate.sessionId;
              const isAnyVerifying = verifyingSessionId !== null;

              return (
                <div
                  key={candidate.sessionId}
                  className="bg-slate-800/80 border border-slate-700/80 rounded-lg p-5 flex flex-col justify-between space-y-4"
                  data-testid={`session-candidate-${candidate.sessionId}`}
                >
                  <div className="space-y-2">
                    <div className="flex items-center justify-between">
                      <span className="font-semibold text-slate-200 capitalize">
                        {candidate.topicId.replace(/-/g, ' ')}
                      </span>
                      <span className="text-[11px] font-mono text-slate-400">
                        {formatDate(candidate.updatedAt)}
                      </span>
                    </div>

                    <div className="text-xs text-slate-400 font-mono">
                      ID: {candidate.sessionId.substring(0, 8)}...
                    </div>

                    {candidate.lastKnownState && (
                      <div className="text-xs text-slate-400">
                        <span className="text-slate-500">Last Recorded Stage: </span>
                        <span className="font-mono text-slate-300">
                          {candidate.lastKnownState}
                        </span>
                      </div>
                    )}
                  </div>

                  <button
                    onClick={() => handleResumeSession(candidate)}
                    disabled={isAnyVerifying}
                    className={`w-full py-2 px-3 rounded text-xs font-medium transition-colors flex items-center justify-center gap-2 ${
                      isVerifying
                        ? 'bg-sky-600/50 text-white cursor-wait'
                        : isAnyVerifying
                        ? 'bg-slate-700 text-slate-400 cursor-not-allowed'
                        : 'bg-slate-700 hover:bg-slate-600 text-slate-200'
                    }`}
                  >
                    {isVerifying ? (
                      <>
                        <span className="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin" />
                        Verifying Session...
                      </>
                    ) : (
                      'Resume Session'
                    )}
                  </button>
                </div>
              );
            })}
          </div>
        )}
      </section>

      {/* System Diagnostic Cards */}
      <section className="space-y-4">
        <h2 className="text-lg font-semibold text-slate-100 tracking-tight">
          System Overview
        </h2>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <div className="bg-slate-800/60 border border-slate-700/60 rounded-lg p-5">
            <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wider font-mono">
              Backend Status
            </h3>
            <div className="mt-3 flex items-center gap-2.5">
              <span
                className={`w-2.5 h-2.5 rounded-full ${
                  health?.status === 'UP' ? 'bg-emerald-400' : 'bg-red-400'
                }`}
              />
              <span className="font-mono text-sm font-medium text-slate-100">
                {isLoadingHealth ? 'Checking...' : health?.status === 'UP' ? 'Connected (v1.0.0)' : 'Offline'}
              </span>
            </div>
            <p className="text-xs text-slate-400 mt-2">
              {health
                ? `${health.activeTopics} active curriculum topics loaded.`
                : 'Backend unreachable.'}
            </p>
          </div>

          <div className="bg-slate-800/60 border border-slate-700/60 rounded-lg p-5">
            <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wider font-mono">
              Evaluator Mode
            </h3>
            <div className="mt-3 flex items-center gap-2.5">
              <span
                className={`w-2.5 h-2.5 rounded-full ${
                  health?.aiConfigured ? 'bg-sky-400' : 'bg-slate-400'
                }`}
              />
              <span className="font-mono text-sm font-medium text-slate-100">
                {health?.aiConfigured ? 'AI Configured' : 'Offline Evaluator'}
              </span>
            </div>
            <p className="text-xs text-slate-400 mt-2">
              {health?.aiConfigured
                ? 'Server has Gemini provider configured.'
                : 'Deterministic rule-based evaluation active ($0 cost).' }
            </p>
          </div>

          <div className="bg-slate-800/60 border border-slate-700/60 rounded-lg p-5">
            <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wider font-mono">
              Learner Identity
            </h3>
            <div className="mt-3 font-mono text-sm font-medium text-slate-200 truncate">
              {learnerId ? learnerId : 'Anonymous (New)'}
            </div>
            <p className="text-xs text-slate-400 mt-2">
              {learnerId
                ? 'Cryptographic capability token active in browser storage.'
                : 'Token will be issued automatically when you start your first session.'}
            </p>
          </div>
        </div>
      </section>
    </div>
  );
};
