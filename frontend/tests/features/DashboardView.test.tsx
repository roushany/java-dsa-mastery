import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DashboardView } from '../../src/features/dashboard/DashboardView';
import { ApiClient } from '../../src/api/client';
import { HealthResponse, SessionResponse } from '../../src/api/types';
import { ApiError } from '../../src/api/errors';
import { saveSession, getStoredSessions } from '../../src/storage/sessionIndexStorage';
import { storeToken } from '../../src/storage/tokenStorage';

describe('DashboardView Component', () => {
  const baseHealth: HealthResponse = {
    status: 'UP',
    version: '1.0.0',
    activeTopics: 7,
    aiConfigured: false,
  };

  const sampleSession: SessionResponse = {
    sessionId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
    learnerId: 'learner-tester',
    topicId: 'binary-search',
    topicTitle: 'Binary Search',
    currentState: 'CONTENT_INTRO',
    mastery: 'NOVICE',
    totalStages: 9,
    completed: false,
  };

  beforeEach(() => {
    localStorage.clear();
  });

  it('renders empty candidate state and responds to curriculum browse navigation', async () => {
    const user = userEvent.setup();
    const onNavigateToCurriculum = vi.fn();

    render(
      <DashboardView
        health={baseHealth}
        isLoadingHealth={false}
        onNavigateToCurriculum={onNavigateToCurriculum}
      />
    );

    expect(screen.getByTestId('empty-sessions')).toBeInTheDocument();
    expect(screen.getByText('No saved sessions found on this device.')).toBeInTheDocument();

    const heroBrowseBtn = screen.getByRole('button', { name: /Browse Curriculum/i });
    await user.click(heroBrowseBtn);
    expect(onNavigateToCurriculum).toHaveBeenCalledTimes(1);

    const inlineBrowseBtn = screen.getByRole('button', {
      name: /Select a topic from the curriculum to get started/i,
    });
    await user.click(inlineBrowseBtn);
    expect(onNavigateToCurriculum).toHaveBeenCalledTimes(2);
  });

  it('renders saved session candidates from localStorage correctly', () => {
    saveSession({
      sessionId: sampleSession.sessionId,
      topicId: sampleSession.topicId,
      createdAt: 1729280000000,
      updatedAt: 1729281000000,
      lastKnownState: 'CONTENT_INTRO',
      lastKnownMastery: 'NOVICE',
    });

    render(
      <DashboardView
        health={baseHealth}
        isLoadingHealth={false}
        onNavigateToCurriculum={vi.fn()}
      />
    );

    expect(screen.getByTestId(`session-candidate-${sampleSession.sessionId}`)).toBeInTheDocument();
    expect(screen.getByText('binary search')).toBeInTheDocument();
    expect(screen.getByText(/ID: aaaaaaaa\.\.\./)).toBeInTheDocument();
    expect(screen.getByText('CONTENT_INTRO')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Resume Session' })).toBeInTheDocument();
  });

  it('verifies and resumes an active session successfully', async () => {
    const user = userEvent.setup();
    const onResumeSession = vi.fn();

    saveSession({
      sessionId: sampleSession.sessionId,
      topicId: sampleSession.topicId,
      createdAt: 1729280000000,
      updatedAt: 1729281000000,
      lastKnownState: 'CONTENT_INTRO',
    });

    const mockClient = {
      getSession: vi.fn().mockResolvedValue({
        ...sampleSession,
        currentState: 'IMPLEMENTATION',
      }),
    } as unknown as ApiClient;

    render(
      <DashboardView
        client={mockClient}
        health={baseHealth}
        isLoadingHealth={false}
        onNavigateToCurriculum={vi.fn()}
        onResumeSession={onResumeSession}
      />
    );

    const resumeBtn = screen.getByRole('button', { name: 'Resume Session' });
    await user.click(resumeBtn);

    expect(mockClient.getSession).toHaveBeenCalledWith(sampleSession.sessionId);

    await waitFor(() => {
      expect(onResumeSession).toHaveBeenCalledWith(
        expect.objectContaining({
          sessionId: sampleSession.sessionId,
          currentState: 'IMPLEMENTATION',
        })
      );
    });

    // Stored session candidate updated with fresh state
    const stored = getStoredSessions();
    expect(stored[0].lastKnownState).toBe('IMPLEMENTATION');
  });

  it('prunes session from storage and UI when server returns 404 SESSION_NOT_FOUND', async () => {
    const user = userEvent.setup();
    const onResumeSession = vi.fn();

    saveSession({
      sessionId: sampleSession.sessionId,
      topicId: sampleSession.topicId,
      createdAt: 1729280000000,
      updatedAt: 1729281000000,
    });

    const notFoundError = new ApiError(404, {
      error: 'SESSION_NOT_FOUND',
      message: 'Session does not exist',
      retryable: false,
      timestamp: Date.now(),
    });

    const mockClient = {
      getSession: vi.fn().mockRejectedValue(notFoundError),
    } as unknown as ApiClient;

    render(
      <DashboardView
        client={mockClient}
        health={baseHealth}
        isLoadingHealth={false}
        onNavigateToCurriculum={vi.fn()}
        onResumeSession={onResumeSession}
      />
    );

    const resumeBtn = screen.getByRole('button', { name: 'Resume Session' });
    await user.click(resumeBtn);

    await waitFor(() => {
      expect(screen.getByTestId('resume-notification')).toBeInTheDocument();
    });

    expect(screen.getByText(/has expired or was purged on the server \(SESSION_NOT_FOUND\)/)).toBeInTheDocument();

    // Session candidate must be purged from storage and UI
    expect(getStoredSessions()).toHaveLength(0);
    expect(screen.queryByTestId(`session-candidate-${sampleSession.sessionId}`)).not.toBeInTheDocument();
    expect(screen.getByTestId('empty-sessions')).toBeInTheDocument();
    expect(onResumeSession).not.toHaveBeenCalled();
  });

  it('prunes session from storage and UI when server returns 410 SESSION_EXPIRED', async () => {
    const user = userEvent.setup();

    saveSession({
      sessionId: sampleSession.sessionId,
      topicId: sampleSession.topicId,
      createdAt: 1729280000000,
      updatedAt: 1729281000000,
    });

    const expiredError = new ApiError(410, {
      error: 'SESSION_EXPIRED',
      message: 'Session idle timeout reached',
      retryable: false,
      timestamp: Date.now(),
    });

    const mockClient = {
      getSession: vi.fn().mockRejectedValue(expiredError),
    } as unknown as ApiClient;

    render(
      <DashboardView
        client={mockClient}
        health={baseHealth}
        isLoadingHealth={false}
        onNavigateToCurriculum={vi.fn()}
      />
    );

    const resumeBtn = screen.getByRole('button', { name: 'Resume Session' });
    await user.click(resumeBtn);

    await waitFor(() => {
      expect(screen.getByTestId('resume-notification')).toBeInTheDocument();
    });

    expect(screen.getByText(/SESSION_EXPIRED/)).toBeInTheDocument();
    expect(getStoredSessions()).toHaveLength(0);
  });

  it('preserves session candidate in storage when resume fails due to network or server error', async () => {
    const user = userEvent.setup();

    saveSession({
      sessionId: sampleSession.sessionId,
      topicId: sampleSession.topicId,
      createdAt: 1729280000000,
      updatedAt: 1729281000000,
    });

    const serverError = new ApiError(500, {
      error: 'INTERNAL_SERVER_ERROR',
      message: 'Database temporarily unavailable',
      retryable: true,
      timestamp: Date.now(),
    });

    const mockClient = {
      getSession: vi.fn().mockRejectedValue(serverError),
    } as unknown as ApiClient;

    render(
      <DashboardView
        client={mockClient}
        health={baseHealth}
        isLoadingHealth={false}
        onNavigateToCurriculum={vi.fn()}
      />
    );

    const resumeBtn = screen.getByRole('button', { name: 'Resume Session' });
    await user.click(resumeBtn);

    await waitFor(() => {
      expect(screen.getByTestId('resume-notification')).toBeInTheDocument();
    });

    expect(screen.getByText(/Unable to resume session: Database temporarily unavailable/)).toBeInTheDocument();

    // Session pointer must NOT be deleted for recoverable server errors
    expect(getStoredSessions()).toHaveLength(1);
    expect(screen.getByTestId(`session-candidate-${sampleSession.sessionId}`)).toBeInTheDocument();
  });

  it('prevents concurrent resume attempts while verification is in-flight', async () => {
    const user = userEvent.setup();
    let resolveGetSession!: (val: SessionResponse) => void;
    const pendingPromise = new Promise<SessionResponse>((resolve) => {
      resolveGetSession = resolve;
    });

    saveSession({
      sessionId: sampleSession.sessionId,
      topicId: sampleSession.topicId,
      createdAt: 1729280000000,
      updatedAt: 1729281000000,
    });

    const mockClient = {
      getSession: vi.fn().mockReturnValue(pendingPromise),
    } as unknown as ApiClient;

    render(
      <DashboardView
        client={mockClient}
        health={baseHealth}
        isLoadingHealth={false}
        onNavigateToCurriculum={vi.fn()}
      />
    );

    const resumeBtn = screen.getByRole('button', { name: 'Resume Session' });
    await user.click(resumeBtn);

    expect(screen.getByText('Verifying Session...')).toBeInTheDocument();
    expect(resumeBtn).toBeDisabled();

    // Extra click
    await user.click(resumeBtn);
    expect(mockClient.getSession).toHaveBeenCalledTimes(1);

    resolveGetSession(sampleSession);

    await waitFor(() => {
      expect(screen.queryByText('Verifying Session...')).not.toBeInTheDocument();
    });
  });

  describe('System Diagnostic Cards', () => {
    it('displays connected backend and offline evaluator mode when aiConfigured is false', () => {
      render(
        <DashboardView
          health={baseHealth}
          isLoadingHealth={false}
          onNavigateToCurriculum={vi.fn()}
        />
      );

      expect(screen.getByText('Connected (v1.0.0)')).toBeInTheDocument();
      expect(screen.getByText('7 active curriculum topics loaded.')).toBeInTheDocument();
      expect(screen.getByText('Offline Evaluator')).toBeInTheDocument();
      expect(screen.getByText('Deterministic rule-based evaluation active ($0 cost).')).toBeInTheDocument();
      expect(screen.getByText('Anonymous (New)')).toBeInTheDocument();
    });

    it('displays AI Configured mode when health reports aiConfigured is true', () => {
      render(
        <DashboardView
          health={{ ...baseHealth, aiConfigured: true }}
          isLoadingHealth={false}
          onNavigateToCurriculum={vi.fn()}
        />
      );

      expect(screen.getByText('AI Configured')).toBeInTheDocument();
      expect(screen.getByText('Server has Gemini provider configured.')).toBeInTheDocument();
    });

    it('displays learner identity if valid capability token exists in storage', () => {
      const token = `learner-98765-abcd.${Date.now()}.validSignatureBase64Url`;
      storeToken(token);

      render(
        <DashboardView
          health={baseHealth}
          isLoadingHealth={false}
          onNavigateToCurriculum={vi.fn()}
        />
      );

      expect(screen.getByText('learner-98765-abcd')).toBeInTheDocument();
      expect(
        screen.getByText('Cryptographic capability token active in browser storage.')
      ).toBeInTheDocument();
    });
  });
});
