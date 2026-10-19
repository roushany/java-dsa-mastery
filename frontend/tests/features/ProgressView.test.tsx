import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProgressView } from '../../src/features/progress/ProgressView';
import { ApiClient } from '../../src/api/client';
import { CurriculumResponse, SessionResponse } from '../../src/api/types';
import { ApiError } from '../../src/api/errors';
import { saveSession } from '../../src/storage/sessionIndexStorage';

describe('ProgressView Component', () => {
  const sampleCurriculum: CurriculumResponse = {
    topics: [
      {
        topicId: 'linear-search',
        title: 'Linear Search',
        difficulty: 'BEGINNER',
        totalStages: 1,
        prerequisites: [],
        hasContent: true,
      },
      {
        topicId: 'binary-search',
        title: 'Binary Search',
        difficulty: 'BEGINNER',
        totalStages: 9,
        prerequisites: ['linear-search'],
        hasContent: true,
      },
    ],
  };

  const activeSession: SessionResponse = {
    sessionId: '11111111-2222-3333-4444-555555555555',
    learnerId: 'learner-alice',
    topicId: 'linear-search',
    topicTitle: 'Linear Search',
    currentState: 'CONCEPT_ASSESSMENT',
    mastery: 'NOVICE',
    totalStages: 1,
    completed: false,
  };

  const completedSession: SessionResponse = {
    sessionId: '22222222-3333-4444-5555-666666666666',
    learnerId: 'learner-alice',
    topicId: 'binary-search',
    topicTitle: 'Binary Search',
    currentState: 'COMPLETED',
    mastery: 'MASTERED',
    totalStages: 9,
    completed: true,
  };

  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('renders scope notice and empty state when no sessions are stored', async () => {
    const onNavigateToCurriculum = vi.fn();
    const mockClient = {
      getCurriculum: vi.fn().mockResolvedValue(sampleCurriculum),
      getSession: vi.fn(),
    } as unknown as ApiClient;

    render(
      <ProgressView
        client={mockClient}
        onNavigateToCurriculum={onNavigateToCurriculum}
        onResumeSession={vi.fn()}
      />
    );

    expect(screen.getByRole('heading', { level: 1, name: 'Learner Progress & Mastery' })).toBeInTheDocument();
    expect(screen.getByTestId('progress-scope-notice')).toBeInTheDocument();
    expect(
      screen.getByText(/This view reflects learning sessions saved on this browser, verified live against the Spring Boot server\./)
    ).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByTestId('empty-progress-state')).toBeInTheDocument();
    });

    expect(screen.getByText('No learning sessions saved in this browser')).toBeInTheDocument();

    const user = userEvent.setup();
    const browseBtn = screen.getByRole('button', { name: 'Browse Curriculum' });
    await user.click(browseBtn);
    expect(onNavigateToCurriculum).toHaveBeenCalledTimes(1);
  });

  it('renders verified active session card with stage, mastery badge, and resume action', async () => {
    saveSession({
      sessionId: activeSession.sessionId,
      topicId: activeSession.topicId,
      createdAt: 1000,
      updatedAt: 1000,
    });

    const user = userEvent.setup();
    const onResumeSession = vi.fn();

    const mockClient = {
      getCurriculum: vi.fn().mockResolvedValue(sampleCurriculum),
      getSession: vi.fn().mockResolvedValue(activeSession),
    } as unknown as ApiClient;

    render(
      <ProgressView
        client={mockClient}
        onNavigateToCurriculum={vi.fn()}
        onResumeSession={onResumeSession}
      />
    );

    await waitFor(() => {
      expect(screen.getByTestId(`session-verified-${activeSession.sessionId}`)).toBeInTheDocument();
    });

    expect(screen.getAllByText('Linear Search').length).toBeGreaterThan(0);
    expect(screen.getByText('Stage: Concept Assessment')).toBeInTheDocument();
    expect(screen.getByText('NOVICE')).toBeInTheDocument();

    const resumeBtn = screen.getByTestId(`btn-resume-${activeSession.sessionId}`);
    expect(resumeBtn).toHaveTextContent('Resume Session');
    await user.click(resumeBtn);

    expect(onResumeSession).toHaveBeenCalledWith(activeSession);
  });

  it('renders completed session card with completed badge and review action', async () => {
    saveSession({
      sessionId: completedSession.sessionId,
      topicId: completedSession.topicId,
      createdAt: 1000,
      updatedAt: 1000,
    });

    const user = userEvent.setup();
    const onResumeSession = vi.fn();

    const mockClient = {
      getCurriculum: vi.fn().mockResolvedValue(sampleCurriculum),
      getSession: vi.fn().mockResolvedValue(completedSession),
    } as unknown as ApiClient;

    render(
      <ProgressView
        client={mockClient}
        onNavigateToCurriculum={vi.fn()}
        onResumeSession={onResumeSession}
      />
    );

    await waitFor(() => {
      expect(screen.getByTestId(`session-verified-${completedSession.sessionId}`)).toBeInTheDocument();
    });

    expect(screen.getByText('✓ Completed')).toBeInTheDocument();
    expect(screen.getByText('MASTERED')).toBeInTheDocument();

    const reviewBtn = screen.getByTestId(`btn-resume-${completedSession.sessionId}`);
    expect(reviewBtn).toHaveTextContent('Review Topic');
    await user.click(reviewBtn);

    expect(onResumeSession).toHaveBeenCalledWith(completedSession);
  });

  it('renders expired session card and removes stale pointer on action', async () => {
    saveSession({
      sessionId: 'eeeeeeee-1111-2222-3333-444455556666',
      topicId: 'linear-search',
      createdAt: 1000,
      updatedAt: 1000,
    });

    const user = userEvent.setup();
    const mockClient = {
      getCurriculum: vi.fn().mockResolvedValue(sampleCurriculum),
      getSession: vi.fn().mockRejectedValue(
        new ApiError(410, {
          error: 'SESSION_EXPIRED',
          message: 'Retention expired',
          retryable: false,
          timestamp: Date.now(),
        })
      ),
    } as unknown as ApiClient;

    render(
      <ProgressView
        client={mockClient}
        onNavigateToCurriculum={vi.fn()}
        onResumeSession={vi.fn()}
      />
    );

    await waitFor(() => {
      expect(screen.getByTestId('session-expired-eeeeeeee-1111-2222-3333-444455556666')).toBeInTheDocument();
    });

    expect(screen.getByText('Expired / Purged')).toBeInTheDocument();

    const removeBtn = screen.getByTestId('btn-remove-eeeeeeee-1111-2222-3333-444455556666');
    await user.click(removeBtn);

    // Stale item removed!
    expect(screen.queryByTestId('session-expired-eeeeeeee-1111-2222-3333-444455556666')).not.toBeInTheDocument();
  });

  it('renders unauthorized session card when token belongs to another learner', async () => {
    saveSession({
      sessionId: 'ffffffff-1111-2222-3333-444455556666',
      topicId: 'binary-search',
      createdAt: 1000,
      updatedAt: 1000,
    });

    const mockClient = {
      getCurriculum: vi.fn().mockResolvedValue(sampleCurriculum),
      getSession: vi.fn().mockRejectedValue(
        new ApiError(403, {
          error: 'FORBIDDEN_ACCESS',
          message: 'Cross-learner access denied',
          retryable: false,
          timestamp: Date.now(),
        })
      ),
    } as unknown as ApiClient;

    render(
      <ProgressView
        client={mockClient}
        onNavigateToCurriculum={vi.fn()}
        onResumeSession={vi.fn()}
      />
    );

    await waitFor(() => {
      expect(screen.getByTestId('session-unauthorized-ffffffff-1111-2222-3333-444455556666')).toBeInTheDocument();
    });

    expect(screen.getByText('Unauthorized')).toBeInTheDocument();
  });

  it('displays curriculum roadmap with real topics and accurate browser status badges', async () => {
    saveSession({
      sessionId: activeSession.sessionId,
      topicId: activeSession.topicId,
      createdAt: 1000,
      updatedAt: 1000,
    });

    const mockClient = {
      getCurriculum: vi.fn().mockResolvedValue(sampleCurriculum),
      getSession: vi.fn().mockResolvedValue(activeSession),
    } as unknown as ApiClient;

    render(
      <ProgressView
        client={mockClient}
        onNavigateToCurriculum={vi.fn()}
        onResumeSession={vi.fn()}
      />
    );

    await waitFor(() => {
      expect(screen.getByTestId('topic-row-linear-search')).toBeInTheDocument();
      expect(screen.getByTestId('topic-row-binary-search')).toBeInTheDocument();
    });

    // Linear search is in progress
    const linearRow = screen.getByTestId('topic-row-linear-search');
    expect(linearRow).toHaveTextContent('In Progress (Concept Assessment)');

    // Binary search has no session in this browser
    const binaryRow = screen.getByTestId('topic-row-binary-search');
    expect(binaryRow).toHaveTextContent('Not Started on this Browser');
  });

  it('displays mastery criteria guide with pedagogical definitions', async () => {
    const mockClient = {
      getCurriculum: vi.fn().mockResolvedValue(sampleCurriculum),
      getSession: vi.fn(),
    } as unknown as ApiClient;

    render(
      <ProgressView
        client={mockClient}
        onNavigateToCurriculum={vi.fn()}
        onResumeSession={vi.fn()}
      />
    );

    await waitFor(() => {
      expect(screen.getByText('Mastery Criteria Explanation')).toBeInTheDocument();
    });

    expect(screen.getByText(/NOVICE:/)).toBeInTheDocument();
    expect(screen.getByText(/FAMILIAR:/)).toBeInTheDocument();
    expect(screen.getByText(/PROFICIENT:/)).toBeInTheDocument();
    expect(screen.getByText(/MASTERED:/)).toBeInTheDocument();
  });
});
