import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../src/App';
import * as useHealthCheckModule from '../src/hooks/useHealthCheck';
import * as useCurriculumModule from '../src/hooks/useCurriculum';
import { HealthResponse, SessionResponse, TopicSummaryDto } from '../src/api/types';
import { ApiError } from '../src/api/errors';
import { saveSession } from '../src/storage/sessionIndexStorage';
import { defaultApiClient } from '../src/api/client';

describe('App Integration', () => {
  const mockHealth: HealthResponse = {
    status: 'UP',
    version: '1.0.0',
    activeTopics: 7,
    aiConfigured: false,
  };

  const mockTopics: TopicSummaryDto[] = [
    {
      topicId: 'linear-search',
      title: 'Linear Search',
      difficulty: 'BEGINNER',
      totalStages: 1,
      prerequisites: [],
      hasContent: true,
    },
  ];

  const mockSession: SessionResponse = {
    sessionId: '12345678-1234-1234-1234-123456789abc',
    learnerId: 'learner-tester',
    topicId: 'linear-search',
    topicTitle: 'Linear Search',
    currentState: 'CONTENT_INTRO',
    mastery: 'NOVICE',
    totalStages: 1,
    completed: false,
  };

  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('renders default Dashboard view with navigation bar', () => {
    vi.spyOn(useHealthCheckModule, 'useHealthCheck').mockReturnValue({
      health: mockHealth,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });

    render(<App />);

    expect(screen.getByRole('heading', { level: 1, name: 'Dashboard' })).toBeInTheDocument();
    expect(screen.getByText('Java DSA Mastery')).toBeInTheDocument();
  });

  it('navigates between navigation tabs cleanly', async () => {
    const user = userEvent.setup();
    vi.spyOn(useHealthCheckModule, 'useHealthCheck').mockReturnValue({
      health: mockHealth,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });

    vi.spyOn(useCurriculumModule, 'useCurriculum').mockReturnValue({
      topics: mockTopics,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });

    render(<App />);

    // Switch to Curriculum
    await user.click(screen.getByRole('button', { name: 'Curriculum' }));
    expect(screen.getByRole('heading', { level: 1, name: 'Curriculum Roadmap' })).toBeInTheDocument();

    // Switch to Progress
    await user.click(screen.getByRole('button', { name: 'Progress' }));
    expect(
      screen.getByRole('heading', { level: 1, name: 'Learner Progress & Mastery' })
    ).toBeInTheDocument();

    // Switch to Learning (empty state initially)
    await user.click(screen.getByRole('button', { name: 'Learning' }));
    expect(screen.getByText('No active session selected.')).toBeInTheDocument();

    // Switch to Settings
    await user.click(screen.getByRole('button', { name: 'Settings' }));
    expect(
      screen.getByRole('heading', { level: 1, name: 'Learner Settings & Capability Identity' })
    ).toBeInTheDocument();
  });

  it('navigates to Learning workspace when a session is started from Curriculum', async () => {
    const user = userEvent.setup();
    vi.spyOn(useHealthCheckModule, 'useHealthCheck').mockReturnValue({
      health: mockHealth,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });

    vi.spyOn(useCurriculumModule, 'useCurriculum').mockReturnValue({
      topics: mockTopics,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });

    vi.spyOn(defaultApiClient, 'startSession').mockResolvedValue(mockSession);

    render(<App />);

    // Navigate to Curriculum
    await user.click(screen.getByRole('button', { name: 'Curriculum' }));

    // Start session
    const startBtn = screen.getByRole('button', { name: 'Start session for Linear Search' });
    await user.click(startBtn);

    // Should transition to Learning tab
    await waitFor(() => {
      expect(screen.getByTestId('active-learning-session')).toBeInTheDocument();
    });

    expect(screen.getByRole('heading', { level: 2, name: 'Linear Search' })).toBeInTheDocument();
    expect(screen.getByText('Stage:')).toBeInTheDocument();
    expect(screen.getByText('CONTENT_INTRO')).toBeInTheDocument();
    expect(screen.getByText(/Session ID: 12345678-1234-1234-1234-123456789abc/)).toBeInTheDocument();
  });

  it('navigates to Learning workspace when a saved session is resumed from Dashboard', async () => {
    const user = userEvent.setup();
    saveSession({
      sessionId: mockSession.sessionId,
      topicId: mockSession.topicId,
      createdAt: 1729280000000,
      updatedAt: 1729281000000,
      lastKnownState: 'CONTENT_INTRO',
    });

    vi.spyOn(useHealthCheckModule, 'useHealthCheck').mockReturnValue({
      health: mockHealth,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });

    vi.spyOn(defaultApiClient, 'getSession').mockResolvedValue(mockSession);

    render(<App />);

    const resumeBtn = screen.getByRole('button', { name: 'Resume Session' });
    await user.click(resumeBtn);

    await waitFor(() => {
      expect(screen.getByTestId('active-learning-session')).toBeInTheDocument();
    });

    expect(screen.getByRole('heading', { level: 2, name: 'Linear Search' })).toBeInTheDocument();
  });

  it('displays backend connection error banner and retries on demand', async () => {
    const user = userEvent.setup();
    const mockRefetch = vi.fn();

    vi.spyOn(useHealthCheckModule, 'useHealthCheck').mockReturnValue({
      health: null,
      isLoading: false,
      error: new ApiError(503, {
        error: 'SERVICE_UNAVAILABLE',
        message: 'Backend server starting',
        retryable: true,
        timestamp: Date.now(),
      }),
      refetch: mockRefetch,
    });

    render(<App />);

    expect(screen.getByText('Backend Connection Failed')).toBeInTheDocument();
    expect(screen.getByText(/Backend server starting/)).toBeInTheDocument();

    const retryBtn = screen.getByRole('button', { name: 'Retry Connection' });
    await user.click(retryBtn);
    expect(mockRefetch).toHaveBeenCalledTimes(1);
  });
});
