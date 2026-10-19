import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CurriculumView } from '../../src/features/curriculum/CurriculumView';
import { ApiClient } from '../../src/api/client';
import { CurriculumResponse, SessionResponse, TopicSummaryDto } from '../../src/api/types';
import { ApiError } from '../../src/api/errors';
import { getStoredSessions } from '../../src/storage/sessionIndexStorage';

describe('CurriculumView Component', () => {
  const sampleTopics: TopicSummaryDto[] = [
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
      difficulty: 'INTERMEDIATE',
      totalStages: 9,
      prerequisites: ['linear-search'],
      hasContent: true,
    },
    {
      topicId: 'graph-traversal',
      title: 'Graph Traversal',
      difficulty: 'ADVANCED',
      totalStages: 9,
      prerequisites: ['binary-search', 'queue'],
      hasContent: false,
    },
  ];

  const sampleCurriculumResponse: CurriculumResponse = {
    topics: sampleTopics,
  };

  const sampleSessionResponse: SessionResponse = {
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
  });

  it('renders loading spinner while fetching curriculum data', () => {
    const mockClient = {
      getCurriculum: vi.fn(() => new Promise(() => {})), // Never resolves
      startSession: vi.fn(),
    } as unknown as ApiClient;

    render(<CurriculumView client={mockClient} />);

    expect(screen.getByTestId('curriculum-loading')).toBeInTheDocument();
    expect(screen.getByText('Loading curriculum from backend...')).toBeInTheDocument();
  });

  it('renders error state and retries fetching curriculum when Retry button is clicked', async () => {
    const user = userEvent.setup();
    const mockClient = {
      getCurriculum: vi
        .fn()
        .mockRejectedValueOnce(
          new ApiError(500, {
            error: 'DATABASE_ERROR',
            message: 'Database connection failed',
            retryable: true,
            timestamp: Date.now(),
          })
        )
        .mockResolvedValueOnce(sampleCurriculumResponse),
      startSession: vi.fn(),
    } as unknown as ApiClient;

    render(<CurriculumView client={mockClient} />);

    await waitFor(() => {
      expect(screen.getByTestId('curriculum-error')).toBeInTheDocument();
    });

    expect(screen.getByText('Unable to load curriculum')).toBeInTheDocument();
    expect(screen.getByText('Database connection failed')).toBeInTheDocument();

    const retryButton = screen.getByRole('button', { name: 'Retry Loading' });
    await user.click(retryButton);

    await waitFor(() => {
      expect(screen.getByTestId('topics-grid')).toBeInTheDocument();
    });

    expect(screen.getByText('Linear Search')).toBeInTheDocument();
    expect(screen.getByText('Binary Search')).toBeInTheDocument();
  });

  it('renders empty state when curriculum has no topics', async () => {
    const mockClient = {
      getCurriculum: vi.fn().mockResolvedValue({ totalTopics: 0, topics: [] }),
      startSession: vi.fn(),
    } as unknown as ApiClient;

    render(<CurriculumView client={mockClient} />);

    await waitFor(() => {
      expect(screen.getByTestId('curriculum-empty')).toBeInTheDocument();
    });

    expect(
      screen.getByText('No curriculum topics are currently available from the backend.')
    ).toBeInTheDocument();
  });

  it('renders curriculum topic cards with correct details and formatting', async () => {
    const mockClient = {
      getCurriculum: vi.fn().mockResolvedValue(sampleCurriculumResponse),
      startSession: vi.fn(),
    } as unknown as ApiClient;

    render(<CurriculumView client={mockClient} />);

    await waitFor(() => {
      expect(screen.getByTestId('topics-grid')).toBeInTheDocument();
    });

    // Topic 1: Linear Search
    expect(screen.getByText('Linear Search')).toBeInTheDocument();
    expect(screen.getByText('BEGINNER')).toBeInTheDocument();
    expect(screen.getByText('None (Foundational)')).toBeInTheDocument();
    expect(screen.getByText('1 stages')).toBeInTheDocument();

    // Topic 2: Binary Search
    expect(screen.getByText('Binary Search')).toBeInTheDocument();
    expect(screen.getByText('INTERMEDIATE')).toBeInTheDocument();
    expect(screen.getByText('linear-search')).toBeInTheDocument();
    expect(screen.getAllByText('9 stages')).toHaveLength(2); // Binary Search and Graph Traversal

    // Topic 3: Graph Traversal
    expect(screen.getByText('Graph Traversal')).toBeInTheDocument();
    expect(screen.getByText('ADVANCED')).toBeInTheDocument();
    expect(screen.getByText('binary-search, queue')).toBeInTheDocument();

    // Content verification badge
    expect(screen.getAllByText('Verified Content')).toHaveLength(2);
  });

  it('starts a new session successfully and saves candidate to storage', async () => {
    const user = userEvent.setup();
    const onSessionStarted = vi.fn();

    const mockClient = {
      getCurriculum: vi.fn().mockResolvedValue(sampleCurriculumResponse),
      startSession: vi.fn().mockResolvedValue(sampleSessionResponse),
    } as unknown as ApiClient;

    render(<CurriculumView client={mockClient} onSessionStarted={onSessionStarted} />);

    await waitFor(() => {
      expect(screen.getByTestId('topics-grid')).toBeInTheDocument();
    });

    const startButton = screen.getByRole('button', { name: 'Start session for Linear Search' });
    await user.click(startButton);

    expect(mockClient.startSession).toHaveBeenCalledTimes(1);
    expect(mockClient.startSession).toHaveBeenCalledWith({ topicId: 'linear-search' });

    await waitFor(() => {
      expect(onSessionStarted).toHaveBeenCalledWith(sampleSessionResponse);
    });

    // Check localStorage saved candidates
    const stored = getStoredSessions();
    expect(stored).toHaveLength(1);
    expect(stored[0].sessionId).toBe(sampleSessionResponse.sessionId);
    expect(stored[0].topicId).toBe('linear-search');
    expect(stored[0].lastKnownState).toBe('CONTENT_INTRO');
    expect(stored[0].lastKnownMastery).toBe('NOVICE');
  });

  it('prevents duplicate submissions while startSession request is in flight', async () => {
    const user = userEvent.setup();
    let resolveStartSession!: (val: SessionResponse) => void;
    const pendingPromise = new Promise<SessionResponse>((resolve) => {
      resolveStartSession = resolve;
    });

    const mockClient = {
      getCurriculum: vi.fn().mockResolvedValue(sampleCurriculumResponse),
      startSession: vi.fn().mockReturnValue(pendingPromise),
    } as unknown as ApiClient;

    render(<CurriculumView client={mockClient} />);

    await waitFor(() => {
      expect(screen.getByTestId('topics-grid')).toBeInTheDocument();
    });

    const linearSearchButton = screen.getByRole('button', { name: 'Start session for Linear Search' });
    const binarySearchButton = screen.getByRole('button', { name: 'Start session for Binary Search' });

    // First click initiates request
    await user.click(linearSearchButton);

    // Button should now indicate active loading
    expect(screen.getByText('Starting Session...')).toBeInTheDocument();
    expect(linearSearchButton).toBeDisabled();
    expect(binarySearchButton).toBeDisabled();

    // Secondary clicks on any topic button should be ignored
    await user.click(linearSearchButton);
    await user.click(binarySearchButton);

    expect(mockClient.startSession).toHaveBeenCalledTimes(1);

    // Resolve the pending request
    resolveStartSession(sampleSessionResponse);

    await waitFor(() => {
      expect(screen.queryByText('Starting Session...')).not.toBeInTheDocument();
    });
  });

  it('handles startSession error cleanly, displays error alert, and does not save pointer', async () => {
    const user = userEvent.setup();
    const rateLimitError = new ApiError(
      429,
      {
        error: 'RATE_LIMIT_EXCEEDED',
        message: 'Too many session requests',
        retryable: false,
        timestamp: Date.now(),
      },
      45
    );

    const mockClient = {
      getCurriculum: vi.fn().mockResolvedValue(sampleCurriculumResponse),
      startSession: vi.fn().mockRejectedValue(rateLimitError),
    } as unknown as ApiClient;

    render(<CurriculumView client={mockClient} />);

    await waitFor(() => {
      expect(screen.getByTestId('topics-grid')).toBeInTheDocument();
    });

    const startButton = screen.getByRole('button', { name: 'Start session for Linear Search' });
    await user.click(startButton);

    await waitFor(() => {
      expect(screen.getByTestId('start-session-error')).toBeInTheDocument();
    });

    expect(screen.getByText('Too many session requests')).toBeInTheDocument();
    expect(screen.getByText(/Rate limit reached\. Please wait 45s before retrying\./)).toBeInTheDocument();

    // Verify localStorage has no saved sessions
    expect(getStoredSessions()).toHaveLength(0);

    // Dismiss the error
    const dismissButton = screen.getByRole('button', { name: 'Dismiss' });
    await user.click(dismissButton);

    expect(screen.queryByTestId('start-session-error')).not.toBeInTheDocument();
  });
});
