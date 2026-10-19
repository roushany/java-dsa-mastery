import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SettingsView } from '../../src/features/settings/SettingsView';
import { ApiClient } from '../../src/api/client';
import { SessionResponse } from '../../src/api/types';
import { ApiError } from '../../src/api/errors';
import { storeToken, getStoredToken } from '../../src/storage/tokenStorage';
import { saveSession, getStoredSessions } from '../../src/storage/sessionIndexStorage';

describe('SettingsView Component', () => {
  const validToken = `learner-tester.${Date.now()}.dGVzdHNpZ25hdHVyZTEyMw`;

  const sampleSession: SessionResponse = {
    sessionId: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
    learnerId: 'learner-tester',
    topicId: 'linear-search',
    topicTitle: 'Linear Search',
    currentState: 'DERIVE',
    mastery: 'FAMILIAR',
    totalStages: 1,
    completed: false,
  };

  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('renders learner identity card and architecture disclosures', () => {
    storeToken(validToken);

    render(<SettingsView />);

    expect(
      screen.getByRole('heading', { level: 1, name: 'Learner Settings & Capability Identity' })
    ).toBeInTheDocument();
    expect(screen.getByText('learner-tester')).toBeInTheDocument();
    expect(screen.getByText(/Anonymous Learner Capability Token/)).toBeInTheDocument();
    expect(screen.getByText(/Token Security Warning:/)).toBeInTheDocument();
    expect(screen.getByText(/Zero Tracking:/)).toBeInTheDocument();
    expect(screen.getByText(/₹0 Budget Target:/)).toBeInTheDocument();
  });

  it('toggles token visibility when Show/Hide button is clicked', async () => {
    storeToken(validToken);
    const user = userEvent.setup();

    render(<SettingsView />);

    expect(screen.getByText(/••••••••/)).toBeInTheDocument();

    const toggleBtn = screen.getByRole('button', { name: 'Show Token' });
    await user.click(toggleBtn);

    expect(screen.getByText(validToken)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Hide Token' })).toBeInTheDocument();
  });

  it('copies token to clipboard when button is clicked', async () => {
    storeToken(validToken);
    const user = userEvent.setup();
    const writeTextMock = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {
      value: {
        writeText: writeTextMock,
      },
      writable: true,
      configurable: true,
    });

    render(<SettingsView />);

    const copyBtn = screen.getByTestId('btn-copy-token');
    await user.click(copyBtn);

    expect(writeTextMock).toHaveBeenCalledWith(validToken);
    expect(screen.getByText(/✓ Copied to Clipboard!/)).toBeInTheDocument();
  });

  it('imports valid token, updates local storage and displays success notification', async () => {
    const user = userEvent.setup();
    const newToken = `learner-newuser.${Date.now()}.bmV3c2lnbmF0dXJlMTIz`;

    render(<SettingsView />);

    const input = screen.getByTestId('input-import-token');
    await user.type(input, newToken);

    const importBtn = screen.getByTestId('btn-import-token');
    await user.click(importBtn);

    expect(
      screen.getByText(/Successfully imported learner token for learner-newuser/)
    ).toBeInTheDocument();
    expect(getStoredToken()).toBe(newToken);
    expect(screen.getByText('learner-newuser')).toBeInTheDocument();
  });

  it('rejects invalid or expired token format without altering storage', async () => {
    storeToken(validToken);
    const user = userEvent.setup();

    render(<SettingsView />);

    const input = screen.getByTestId('input-import-token');
    await user.type(input, 'invalid-malformed-token');

    const importBtn = screen.getByTestId('btn-import-token');
    await user.click(importBtn);

    expect(
      screen.getByText(/Invalid learner token format or expired token/)
    ).toBeInTheDocument();
    // Stored token preserved!
    expect(getStoredToken()).toBe(validToken);
  });

  it('recovers session by valid UUID and provides immediate resume action', async () => {
    storeToken(validToken);
    const user = userEvent.setup();
    const onResumeSession = vi.fn();
    const mockClient = {
      getSession: vi.fn().mockResolvedValue(sampleSession),
    } as unknown as ApiClient;

    render(
      <SettingsView client={mockClient} onResumeSession={onResumeSession} />
    );

    const input = screen.getByTestId('input-recovery-session-id');
    await user.type(input, sampleSession.sessionId);

    const recoverBtn = screen.getByTestId('btn-recover-session');
    await user.click(recoverBtn);

    await waitFor(() => {
      expect(screen.getByTestId('recovered-session-card')).toBeInTheDocument();
    });

    expect(
      screen.getByText(/✓ Session Successfully Recovered!/)
    ).toBeInTheDocument();
    expect(screen.getByText('Linear Search')).toBeInTheDocument();
    expect(screen.getByText('FAMILIAR')).toBeInTheDocument();

    // Stored in session index
    expect(getStoredSessions()).toHaveLength(1);
    expect(getStoredSessions()[0].sessionId).toBe(sampleSession.sessionId);

    const resumeBtn = screen.getByTestId('btn-resume-recovered');
    await user.click(resumeBtn);
    expect(onResumeSession).toHaveBeenCalledWith(sampleSession);
  });

  it('explains that learner token is missing when attempting session recovery without stored token', async () => {
    const user = userEvent.setup();
    const mockClient = {
      getSession: vi.fn(),
    } as unknown as ApiClient;

    render(<SettingsView client={mockClient} />);

    const input = screen.getByTestId('input-recovery-session-id');
    await user.type(input, '11111111-2222-3333-4444-555555555555');

    const recoverBtn = screen.getByTestId('btn-recover-session');
    await user.click(recoverBtn);

    expect(
      screen.getByText(
        'Missing learner token: please import your saved learner token first before attempting to recover sessions.'
      )
    ).toBeInTheDocument();
    expect(mockClient.getSession).not.toHaveBeenCalled();
  });

  it('handles session recovery errors on 404/410 and 403', async () => {
    storeToken(validToken);
    const user = userEvent.setup();
    const mockClient = {
      getSession: vi
        .fn()
        .mockRejectedValueOnce(
          new ApiError(404, {
            error: 'SESSION_NOT_FOUND',
            message: 'Session not found',
            retryable: false,
            timestamp: Date.now(),
          })
        )
        .mockRejectedValueOnce(
          new ApiError(403, {
            error: 'FORBIDDEN_ACCESS',
            message: 'Cross-learner access denied',
            retryable: false,
            timestamp: Date.now(),
          })
        ),
    } as unknown as ApiClient;

    render(<SettingsView client={mockClient} />);

    const input = screen.getByTestId('input-recovery-session-id');
    const recoverBtn = screen.getByTestId('btn-recover-session');

    // 1. Test 404
    await user.type(input, '11111111-2222-3333-4444-555555555555');
    await user.click(recoverBtn);

    await waitFor(() => {
      expect(
        screen.getByText('Session was not found or has expired on the server (HTTP 404/410).')
      ).toBeInTheDocument();
    });

    // 2. Test 403
    await user.clear(input);
    await user.type(input, '22222222-3333-4444-5555-666666666666');
    await user.click(recoverBtn);

    await waitFor(() => {
      expect(
        screen.getByText(
          'Unauthorized: this session was created under a different learner token than the active token in this browser (HTTP 403).'
        )
      ).toBeInTheDocument();
    });
  });

  it('validates UUID format before attempting session recovery', async () => {
    const user = userEvent.setup();
    const mockClient = {
      getSession: vi.fn(),
    } as unknown as ApiClient;

    render(<SettingsView client={mockClient} />);

    const input = screen.getByTestId('input-recovery-session-id');
    await user.type(input, 'not-a-valid-uuid');

    const recoverBtn = screen.getByTestId('btn-recover-session');
    await user.click(recoverBtn);

    expect(screen.getByText(/Invalid UUID format/)).toBeInTheDocument();
    expect(mockClient.getSession).not.toHaveBeenCalled();
  });

  it('confirms and clears stored sessions index', async () => {
    saveSession({
      sessionId: '11111111-2222-3333-4444-555555555555',
      topicId: 'linear-search',
      createdAt: 1000,
      updatedAt: 1000,
    });
    const user = userEvent.setup();

    render(<SettingsView />);

    expect(screen.getByText('Clear Stored Sessions (1)')).toBeInTheDocument();

    const promptBtn = screen.getByTestId('btn-prompt-clear-sessions');
    await user.click(promptBtn);

    expect(
      screen.getByText(/Are you sure you want to clear your stored session index?/)
    ).toBeInTheDocument();

    const confirmBtn = screen.getByTestId('btn-confirm-reset');
    await user.click(confirmBtn);

    expect(getStoredSessions()).toHaveLength(0);
    expect(
      screen.getByText('Stored browser session references have been cleared.')
    ).toBeInTheDocument();
  });

  it('confirms and resets identity and session index', async () => {
    storeToken(validToken);
    saveSession({
      sessionId: '11111111-2222-3333-4444-555555555555',
      topicId: 'linear-search',
      createdAt: 1000,
      updatedAt: 1000,
    });
    const user = userEvent.setup();

    render(<SettingsView />);

    const promptBtn = screen.getByTestId('btn-prompt-reset-identity');
    await user.click(promptBtn);

    expect(
      screen.getByText(/Are you sure you want to reset your learner identity\?/)
    ).toBeInTheDocument();
    expect(
      screen.getByText(/without a saved learner-token backup/)
    ).toBeInTheDocument();

    const confirmBtn = screen.getByTestId('btn-confirm-reset');
    await user.click(confirmBtn);

    expect(getStoredToken()).toBeNull();
    expect(getStoredSessions()).toHaveLength(0);
    expect(
      screen.getByText(/Identity and session references cleared\./)
    ).toBeInTheDocument();
  });
});
