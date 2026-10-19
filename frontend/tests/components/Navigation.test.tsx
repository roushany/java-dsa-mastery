import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Navigation } from '../../src/components/Navigation';
import { HealthResponse } from '../../src/api/types';
import { ApiError } from '../../src/api/errors';

describe('Navigation component', () => {
  const baseHealth: HealthResponse = {
    status: 'UP',
    version: '1.0.0',
    activeTopics: 7,
    aiConfigured: true,
  };

  it('renders brand and navigation tabs', () => {
    const onSelectTab = vi.fn();
    render(
      <Navigation
        currentTab="dashboard"
        onSelectTab={onSelectTab}
        health={baseHealth}
        isLoadingHealth={false}
        healthError={null}
      />
    );

    expect(screen.getByText('Java DSA Mastery')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Dashboard' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Curriculum' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Learning' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Progress' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Settings' })).toBeInTheDocument();
  });

  it('triggers onSelectTab when a tab is clicked', async () => {
    const user = userEvent.setup();
    const onSelectTab = vi.fn();

    render(
      <Navigation
        currentTab="dashboard"
        onSelectTab={onSelectTab}
        health={baseHealth}
        isLoadingHealth={false}
        healthError={null}
      />
    );

    await user.click(screen.getByRole('button', { name: 'Curriculum' }));
    expect(onSelectTab).toHaveBeenCalledWith('curriculum');

    await user.click(screen.getByRole('button', { name: 'Settings' }));
    expect(onSelectTab).toHaveBeenCalledWith('settings');
  });

  it('displays "Connecting..." when loading health status', () => {
    render(
      <Navigation
        currentTab="dashboard"
        onSelectTab={vi.fn()}
        health={null}
        isLoadingHealth={true}
        healthError={null}
      />
    );

    expect(screen.getByText('Connecting...')).toBeInTheDocument();
  });

  describe('Independent Backend Connectivity Indicator', () => {
    it('displays "API Connected" when backend status is UP', () => {
      render(
        <Navigation
          currentTab="dashboard"
          onSelectTab={vi.fn()}
          health={baseHealth}
          isLoadingHealth={false}
          healthError={null}
        />
      );

      const backendBadge = screen.getByTestId('backend-status');
      expect(backendBadge).toHaveTextContent('API Connected');
    });

    it('displays "API Degraded" when backend status is not UP', () => {
      render(
        <Navigation
          currentTab="dashboard"
          onSelectTab={vi.fn()}
          health={{ ...baseHealth, status: 'DOWN' }}
          isLoadingHealth={false}
          healthError={null}
        />
      );

      const backendBadge = screen.getByTestId('backend-status');
      expect(backendBadge).toHaveTextContent('API Degraded');
    });

    it('displays "Backend Offline" when health check encounters error', () => {
      const mockError = new ApiError(500, {
        error: 'INTERNAL_SERVER_ERROR',
        message: 'Server unreachable',
        retryable: true,
        timestamp: Date.now(),
      });

      render(
        <Navigation
          currentTab="dashboard"
          onSelectTab={vi.fn()}
          health={null}
          isLoadingHealth={false}
          healthError={mockError}
        />
      );

      const backendBadge = screen.getByTestId('backend-status');
      expect(backendBadge).toHaveTextContent('Backend Offline');
    });
  });

  describe('Independent AI Configuration Indicator', () => {
    it('displays "AI Configured" (and NOT "AI Active") when aiConfigured is true', () => {
      render(
        <Navigation
          currentTab="dashboard"
          onSelectTab={vi.fn()}
          health={{ ...baseHealth, aiConfigured: true }}
          isLoadingHealth={false}
          healthError={null}
        />
      );

      const aiBadge = screen.getByTestId('ai-status');
      expect(aiBadge).toHaveTextContent('AI Configured');
      expect(screen.queryByText('AI Active')).not.toBeInTheDocument();
    });

    it('displays "Offline Evaluator" when aiConfigured is false', () => {
      render(
        <Navigation
          currentTab="dashboard"
          onSelectTab={vi.fn()}
          health={{ ...baseHealth, aiConfigured: false }}
          isLoadingHealth={false}
          healthError={null}
        />
      );

      const aiBadge = screen.getByTestId('ai-status');
      expect(aiBadge).toHaveTextContent('Offline Evaluator');
    });

    it('does not display AI badge when backend is offline', () => {
      render(
        <Navigation
          currentTab="dashboard"
          onSelectTab={vi.fn()}
          health={null}
          isLoadingHealth={false}
          healthError={new ApiError(0, { error: 'NETWORK_ERROR', message: 'Down', retryable: true, timestamp: Date.now() })}
        />
      );

      expect(screen.queryByTestId('ai-status')).not.toBeInTheDocument();
    });
  });
});
