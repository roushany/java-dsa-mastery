import React from 'react';
import { HealthResponse } from '../api/types';
import { ApiError } from '../api/errors';

export type NavigationTab = 'dashboard' | 'curriculum' | 'learning' | 'progress' | 'settings';

interface NavigationProps {
  currentTab: NavigationTab;
  onSelectTab: (tab: NavigationTab) => void;
  health: HealthResponse | null;
  isLoadingHealth: boolean;
  isWakingUp?: boolean;
  healthError: ApiError | null;
}

export const Navigation: React.FC<NavigationProps> = ({
  currentTab,
  onSelectTab,
  health,
  isLoadingHealth,
  isWakingUp = false,
  healthError,
}) => {
  const tabs: Array<{ id: NavigationTab; label: string }> = [
    { id: 'dashboard', label: 'Dashboard' },
    { id: 'curriculum', label: 'Curriculum' },
    { id: 'learning', label: 'Learning' },
    { id: 'progress', label: 'Progress' },
    { id: 'settings', label: 'Settings' },
  ];

  return (
    <header className="bg-slate-900 border-b border-slate-800 sticky top-0 z-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          {/* Brand */}
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded bg-sky-500 flex items-center justify-center font-mono font-bold text-slate-950 text-lg">
              J
            </div>
            <span className="font-semibold text-lg text-slate-100 tracking-tight">
              Java DSA Mastery
            </span>
          </div>

          {/* Navigation Links */}
          <nav className="flex space-x-1 sm:space-x-4" aria-label="Main Navigation">
            {tabs.map((tab) => {
              const isActive = currentTab === tab.id;
              return (
                <button
                  key={tab.id}
                  onClick={() => onSelectTab(tab.id)}
                  className={`px-3 py-2 rounded-md text-sm font-medium transition-colors ${
                    isActive
                      ? 'bg-slate-800 text-sky-400 font-semibold'
                      : 'text-slate-300 hover:bg-slate-800/60 hover:text-slate-100'
                  }`}
                  aria-current={isActive ? 'page' : undefined}
                >
                  {tab.label}
                </button>
              );
            })}
          </nav>

          {/* Independent Backend Connectivity & AI Status Indicators */}
          <div className="flex items-center gap-2 text-xs font-mono">
            {/* 1. Independent Backend Connectivity Indicator */}
            {isWakingUp ? (
              <span
                className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-amber-950/60 border border-amber-800/50 text-amber-300"
                title="Free cloud backend is waking up from sleep. Reconnecting automatically..."
                data-testid="backend-status"
              >
                <span className="w-2 h-2 rounded-full bg-amber-400 animate-pulse" />
                <span>Waking Up...</span>
              </span>
            ) : isLoadingHealth ? (
              <span className="inline-flex items-center gap-1.5 text-slate-400 px-2 py-1">
                <span className="w-2 h-2 rounded-full bg-slate-500 animate-pulse" />
                Connecting...
              </span>
            ) : health ? (
              <span
                className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full border ${
                  health.status === 'UP'
                    ? 'bg-slate-800 border-slate-700 text-slate-300'
                    : 'bg-amber-950/60 border-amber-800/50 text-amber-300'
                }`}
                title={`Backend v${health.version} - Status: ${health.status} (${health.activeTopics} Active Topics)`}
                data-testid="backend-status"
              >
                <span
                  className={`w-2 h-2 rounded-full ${
                    health.status === 'UP' ? 'bg-emerald-400' : 'bg-amber-400'
                  }`}
                />
                <span>{health.status === 'UP' ? 'API Connected' : 'API Degraded'}</span>
              </span>
            ) : (
              <span
                className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-red-950/60 border border-red-800/50 text-red-300"
                title={healthError?.message || 'Cannot connect to backend server'}
                data-testid="backend-status"
              >
                <span className="w-2 h-2 rounded-full bg-red-500" />
                Backend Offline
              </span>
            )}

            {/* 2. Independent AI / Evaluator Configuration Indicator */}
            {health && (
              <span
                className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-slate-800 border border-slate-700 text-slate-300"
                title={
                  health.aiConfigured
                    ? 'Gemini AI provider configured on backend server'
                    : 'Deterministic mock evaluator active; no external AI keys required'
                }
                data-testid="ai-status"
              >
                <span
                  className={`w-2 h-2 rounded-full ${
                    health.aiConfigured ? 'bg-sky-400' : 'bg-slate-400'
                  }`}
                />
                <span>{health.aiConfigured ? 'AI Configured' : 'Offline Evaluator'}</span>
              </span>
            )}
          </div>
        </div>
      </div>
    </header>
  );
};
