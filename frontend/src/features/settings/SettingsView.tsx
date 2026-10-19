import React, { useState } from 'react';
import { ApiClient, defaultApiClient } from '../../api/client';
import { ApiError } from '../../api/errors';
import { SessionResponse } from '../../api/types';
import {
  clearSessionIndex,
  saveSession,
  getStoredSessions,
} from '../../storage/sessionIndexStorage';
import {
  clearStoredToken,
  getLearnerIdFromToken,
  getStoredToken,
  isValidLearnerToken,
  storeToken,
} from '../../storage/tokenStorage';

interface SettingsViewProps {
  client?: ApiClient;
  onResumeSession?: (session: SessionResponse) => void;
}

const UUID_REGEX =
  /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

export const SettingsView: React.FC<SettingsViewProps> = ({
  client = defaultApiClient,
  onResumeSession,
}) => {
  // Token state
  const [currentToken, setCurrentToken] = useState<string | null>(() => getStoredToken());
  const [showToken, setShowToken] = useState<boolean>(false);
  const [tokenCopied, setTokenCopied] = useState<boolean>(false);

  // Import token state
  const [importInput, setImportInput] = useState<string>('');
  const [importNotification, setImportNotification] = useState<{
    type: 'success' | 'error';
    message: string;
  } | null>(null);

  // Session recovery state
  const [recoverySessionId, setRecoverySessionId] = useState<string>('');
  const [isRecovering, setIsRecovering] = useState<boolean>(false);
  const [recoveredSession, setRecoveredSession] = useState<SessionResponse | null>(null);
  const [recoveryError, setRecoveryError] = useState<string | null>(null);

  // Storage reset confirmation
  const [confirmReset, setConfirmReset] = useState<'sessions' | 'identity' | null>(null);
  const [storageNotification, setStorageNotification] = useState<string | null>(null);

  const learnerId = getLearnerIdFromToken(currentToken);

  // 1. Copy Token to Clipboard
  const handleCopyToken = async () => {
    if (!currentToken) return;
    try {
      if (navigator?.clipboard?.writeText) {
        await navigator.clipboard.writeText(currentToken);
      }
      setTokenCopied(true);
      setTimeout(() => setTokenCopied(false), 3000);
    } catch {
      // Fallback
      setTokenCopied(true);
      setTimeout(() => setTokenCopied(false), 3000);
    }
  };

  // 2. Import Token
  const handleImportToken = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = importInput.trim();
    setImportNotification(null);

    if (!trimmed) {
      setImportNotification({
        type: 'error',
        message: 'Please paste a token into the field.',
      });
      return;
    }

    if (!isValidLearnerToken(trimmed)) {
      setImportNotification({
        type: 'error',
        message:
          'Invalid learner token format or expired token. Must match <learnerId>.<timestampMs>.<base64urlSignature> within the 30-day validity window.',
      });
      return;
    }

    const success = storeToken(trimmed);
    if (success) {
      setCurrentToken(trimmed);
      setImportInput('');
      setImportNotification({
        type: 'success',
        message: `Successfully imported learner token for ${getLearnerIdFromToken(trimmed)}. Future sessions will use this identity.`,
      });
    } else {
      setImportNotification({
        type: 'error',
        message: 'Unable to write token to browser storage.',
      });
    }
  };

  // 3. Recover Session by UUID
  const handleRecoverSession = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = recoverySessionId.trim();
    setRecoveryError(null);
    setRecoveredSession(null);

    if (!trimmed) {
      setRecoveryError('Please enter a session UUID.');
      return;
    }

    if (!UUID_REGEX.test(trimmed)) {
      setRecoveryError('Invalid UUID format. Example: 3fa85f64-5717-4562-b3fc-2c963f66afa6');
      return;
    }

    if (!currentToken) {
      setRecoveryError(
        'Missing learner token: please import your saved learner token first before attempting to recover sessions.'
      );
      return;
    }

    setIsRecovering(true);

    try {
      const session = await client.getSession(trimmed);
      saveSession({
        sessionId: session.sessionId,
        topicId: session.topicId,
        createdAt: Date.now(),
        updatedAt: Date.now(),
        lastKnownState: session.currentState,
        lastKnownMastery: session.mastery,
      });

      setRecoveredSession(session);
      setRecoverySessionId('');
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        if (err.isNotFound || err.isSessionExpired) {
          setRecoveryError('Session was not found or has expired on the server (HTTP 404/410).');
        } else if (err.isForbidden || err.isUnauthorized) {
          setRecoveryError(
            'Unauthorized: this session was created under a different learner token than the active token in this browser (HTTP 403).'
          );
        } else {
          setRecoveryError(`Unable to verify session: ${err.message}`);
        }
      } else {
        setRecoveryError('Network error connecting to the Spring Boot server.');
      }
    } finally {
      setIsRecovering(false);
    }
  };

  // 4. Clear Local Storage
  const handleClearSessions = () => {
    clearSessionIndex();
    setConfirmReset(null);
    setStorageNotification('Stored browser session references have been cleared.');
    setTimeout(() => setStorageNotification(null), 4000);
  };

  const handleResetIdentity = () => {
    clearStoredToken();
    clearSessionIndex();
    setCurrentToken(null);
    setConfirmReset(null);
    setStorageNotification('Identity and session references cleared. A fresh anonymous identity will be minted on your next session.');
    setTimeout(() => setStorageNotification(null), 4000);
  };

  return (
    <div className="space-y-8" data-testid="settings-view">
      {/* Header */}
      <div className="pb-2 border-b border-slate-800">
        <h1 className="text-2xl font-bold text-slate-50 tracking-tight">
          Learner Settings &amp; Capability Identity
        </h1>
        <p className="text-slate-400 text-sm mt-1">
          Manage your anonymous learner capability token, recover active sessions, and configure browser storage.
        </p>
      </div>

      {/* Storage Notification Banner */}
      {storageNotification && (
        <div
          className="p-4 rounded-lg bg-emerald-950/40 border border-emerald-800/60 text-sm text-emerald-200"
          role="status"
        >
          {storageNotification}
        </div>
      )}

      {/* Anonymous Identity & Capability Token Card */}
      <div className="p-6 rounded-lg bg-slate-800/60 border border-slate-700 space-y-4">
        <div>
          <h2 className="text-base font-semibold text-slate-100 flex items-center gap-2">
            <span>🔑</span> Anonymous Learner Capability Token
          </h2>
          <p className="text-xs text-slate-400 mt-1">
            Java DSA Mastery utilizes anonymous cryptographic capability tokens rather than traditional passwords or email registration.
          </p>
        </div>

        <div className="p-4 rounded bg-slate-900/80 border border-slate-800 space-y-3">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 text-xs">
            <span className="text-slate-400 font-mono">Current Learner ID:</span>
            <span className="font-mono text-sky-400 font-semibold truncate">
              {learnerId ?? 'No Identity Minted (Will be generated on first session)'}
            </span>
          </div>

          {currentToken ? (
            <div className="space-y-2">
              <div className="flex items-center justify-between text-xs">
                <span className="text-slate-400 font-mono">Active Token:</span>
                <button
                  type="button"
                  onClick={() => setShowToken(!showToken)}
                  className="text-sky-400 hover:text-sky-300 text-xs font-mono underline"
                >
                  {showToken ? 'Hide Token' : 'Show Token'}
                </button>
              </div>

              <div className="p-2.5 rounded bg-slate-950 border border-slate-800 font-mono text-xs text-slate-300 break-all select-all">
                {showToken ? currentToken : `${currentToken.slice(0, 16)}••••••••••••••••••••••••••••••••••••`}
              </div>

              <div className="flex items-center justify-between pt-1">
                <button
                  type="button"
                  onClick={handleCopyToken}
                  className="px-3 py-1.5 rounded bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-medium border border-slate-700 transition-colors"
                  data-testid="btn-copy-token"
                >
                  {tokenCopied ? '✓ Copied to Clipboard!' : 'Copy Token to Clipboard'}
                </button>

                <span className="text-xs font-mono text-slate-500">
                  Validity: 30 days from creation
                </span>
              </div>
            </div>
          ) : (
            <p className="text-xs text-slate-400 italic">
              No learner token is currently stored. Start a session from the Curriculum roadmap to generate your identity token.
            </p>
          )}
        </div>

        {/* Security Warning Notice */}
        <div className="p-3 rounded bg-amber-950/20 border border-amber-800/40 text-xs text-amber-300/90 space-y-1">
          <p className="font-semibold text-amber-200">⚠ Token Security Warning:</p>
          <p>
            Your learner capability token grants access to your practice sessions on the backend. Do not share, publish, or log this token in public repositories or unvetted environments.
          </p>
        </div>
      </div>

      {/* Import Existing Token Card */}
      <div className="p-6 rounded-lg bg-slate-800/60 border border-slate-700 space-y-4">
        <div>
          <h2 className="text-base font-semibold text-slate-100 flex items-center gap-2">
            <span>📥</span> Import Existing Learner Token
          </h2>
          <p className="text-xs text-slate-400 mt-1">
            To continue learning from another browser or device, import your previously saved capability token.
          </p>
        </div>

        <form onSubmit={handleImportToken} className="space-y-3">
          <textarea
            value={importInput}
            onChange={(e) => setImportInput(e.target.value)}
            rows={2}
            placeholder="Paste your learner token here (<learnerId>.<timestamp>.<signature>)..."
            className="w-full p-2.5 rounded bg-slate-950 border border-slate-700 text-xs font-mono text-slate-100 placeholder-slate-600 focus:outline-none focus:ring-1 focus:ring-sky-500"
            data-testid="input-import-token"
          />

          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <span className="text-xs text-slate-500">
              Only cryptographically well-formed tokens within the 30-day window will be accepted.
            </span>
            <button
              type="submit"
              className="px-4 py-2 rounded bg-sky-600 hover:bg-sky-500 text-white text-xs font-medium transition-colors"
              data-testid="btn-import-token"
            >
              Import Token
            </button>
          </div>
        </form>

        {importNotification && (
          <div
            className={`p-3 rounded text-xs ${
              importNotification.type === 'success'
                ? 'bg-emerald-950/40 border border-emerald-800 text-emerald-200'
                : 'bg-red-950/40 border border-red-800 text-red-200'
            }`}
            role="status"
            data-testid="import-notification"
          >
            {importNotification.message}
          </div>
        )}
      </div>

      {/* Session Recovery by ID Card */}
      <div className="p-6 rounded-lg bg-slate-800/60 border border-slate-700 space-y-4">
        <div>
          <h2 className="text-base font-semibold text-slate-100 flex items-center gap-2">
            <span>🔍</span> Recover Session by ID
          </h2>
          <p className="text-xs text-slate-400 mt-1">
            If you cleared your browser data or lost your session pointer, you can re-index any unexpired session using its UUID.
          </p>
        </div>

        <form onSubmit={handleRecoverSession} className="space-y-3">
          <div className="flex flex-col sm:flex-row gap-3">
            <input
              type="text"
              value={recoverySessionId}
              onChange={(e) => setRecoverySessionId(e.target.value)}
              placeholder="Enter session UUID (e.g. 3fa85f64-5717-4562-b3fc-2c963f66afa6)..."
              className="flex-1 p-2.5 rounded bg-slate-950 border border-slate-700 text-xs font-mono text-slate-100 placeholder-slate-600 focus:outline-none focus:ring-1 focus:ring-sky-500"
              data-testid="input-recovery-session-id"
            />
            <button
              type="submit"
              disabled={isRecovering}
              className="px-4 py-2 rounded bg-slate-700 hover:bg-slate-600 text-white text-xs font-medium transition-colors whitespace-nowrap"
              data-testid="btn-recover-session"
            >
              {isRecovering ? 'Verifying...' : 'Recover Session'}
            </button>
          </div>
        </form>

        {recoveryError && (
          <div
            className="p-3 rounded bg-red-950/40 border border-red-800 text-xs text-red-200"
            role="alert"
            data-testid="recovery-error"
          >
            {recoveryError}
          </div>
        )}

        {recoveredSession && (
          <div
            className="p-4 rounded-lg bg-emerald-950/30 border border-emerald-800/60 space-y-3 text-xs text-emerald-200"
            data-testid="recovered-session-card"
          >
            <div className="flex items-center justify-between">
              <span className="font-semibold text-emerald-100 text-sm">
                ✓ Session Successfully Recovered!
              </span>
              <span className="font-mono text-xs px-2 py-0.5 rounded bg-emerald-950 text-emerald-300 border border-emerald-800">
                {recoveredSession.mastery}
              </span>
            </div>
            <p>
              Topic: <strong>{recoveredSession.topicTitle}</strong> | Stage: <strong>{recoveredSession.currentState}</strong>
            </p>
            <div className="pt-2 border-t border-emerald-900/50 flex justify-end">
              {onResumeSession && (
                <button
                  type="button"
                  onClick={() => onResumeSession(recoveredSession)}
                  className="px-3.5 py-1.5 rounded bg-emerald-600 hover:bg-emerald-500 text-white font-medium text-xs transition-colors"
                  data-testid="btn-resume-recovered"
                >
                  Resume Session Now
                </button>
              )}
            </div>
          </div>
        )}
      </div>

      {/* Browser Storage Management & Reset */}
      <div className="p-6 rounded-lg bg-slate-800/60 border border-slate-700 space-y-4">
        <div>
          <h2 className="text-base font-semibold text-slate-100 flex items-center gap-2">
            <span>🗑</span> Browser Storage &amp; Privacy Management
          </h2>
          <p className="text-xs text-slate-400 mt-1">
            All session pointers and your learner capability token are stored in this browser&#39;s local storage.
          </p>
        </div>

        {confirmReset === null ? (
          <div className="flex flex-wrap gap-4 pt-2">
            <button
              type="button"
              onClick={() => setConfirmReset('sessions')}
              className="px-4 py-2 rounded bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-medium border border-slate-700 transition-colors"
              data-testid="btn-prompt-clear-sessions"
            >
              Clear Stored Sessions ({getStoredSessions().length})
            </button>
            <button
              type="button"
              onClick={() => setConfirmReset('identity')}
              className="px-4 py-2 rounded bg-red-950/40 hover:bg-red-900/50 text-red-300 text-xs font-medium border border-red-800/60 transition-colors"
              data-testid="btn-prompt-reset-identity"
            >
              Reset Identity &amp; Start Fresh
            </button>
          </div>
        ) : (
          <div className="p-4 rounded-lg bg-slate-900 border border-amber-800/60 space-y-3">
            <p className="text-xs text-amber-200">
              {confirmReset === 'sessions'
                ? 'Are you sure you want to clear your stored session index? Your session pointers will be removed from this browser.'
                : 'Are you sure you want to reset your learner identity? Your active token and all session pointers will be removed, and any active sessions on the server will become inaccessible without a saved learner-token backup. You will start with a fresh anonymous identity.'}
            </p>
            <div className="flex items-center gap-3">
              <button
                type="button"
                onClick={confirmReset === 'sessions' ? handleClearSessions : handleResetIdentity}
                className="px-3.5 py-1.5 rounded bg-red-700 hover:bg-red-600 text-white text-xs font-medium transition-colors"
                data-testid="btn-confirm-reset"
              >
                Yes, Confirm Reset
              </button>
              <button
                type="button"
                onClick={() => setConfirmReset(null)}
                className="px-3.5 py-1.5 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-medium transition-colors"
                data-testid="btn-cancel-reset"
              >
                Cancel
              </button>
            </div>
          </div>
        )}
      </div>

      {/* System Architecture Disclosures */}
      <div className="p-6 rounded-lg bg-slate-800/40 border border-slate-700 space-y-2 text-xs text-slate-400">
        <h3 className="text-sm font-semibold text-slate-200">System Architecture Disclosures</h3>
        <ul className="space-y-1 list-disc list-inside text-slate-400">
          <li><strong>Zero Tracking:</strong> No tracking cookies, telemetry pixels, or third-party user analytics.</li>
          <li><strong>₹0 Budget Target:</strong> Designed to run on free-tier PostgreSQL and local open-source containers.</li>
          <li><strong>AI Fallback:</strong> Operates fully deterministically without requiring a Gemini API key.</li>
          <li><strong>Self-Hostable:</strong> Standard Spring Boot REST backend with Flyway-managed PostgreSQL migrations.</li>
        </ul>
      </div>
    </div>
  );
};
