import { useState } from 'react';
import { Navigation, NavigationTab } from './components/Navigation';
import { useHealthCheck } from './hooks/useHealthCheck';
import { DashboardView } from './features/dashboard/DashboardView';
import { CurriculumView } from './features/curriculum/CurriculumView';
import { LearningWorkspace } from './features/learning/LearningWorkspace';
import { ProgressView } from './features/progress/ProgressView';
import { SettingsView } from './features/settings/SettingsView';
import { SessionResponse } from './api/types';

export function App() {
  const [currentTab, setCurrentTab] = useState<NavigationTab>('dashboard');
  const [activeSession, setActiveSession] = useState<SessionResponse | null>(null);
  const { health, isLoading, isWakingUp, error, refetch } = useHealthCheck();

  const handleSessionStartedOrResumed = (session: SessionResponse) => {
    setActiveSession(session);
    setCurrentTab('learning');
  };

  return (
    <div className="min-h-screen bg-slate-900 text-slate-100 flex flex-col font-sans">
      <Navigation
        currentTab={currentTab}
        onSelectTab={setCurrentTab}
        health={health}
        isLoadingHealth={isLoading}
        isWakingUp={isWakingUp}
        healthError={error}
      />

      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Backend Waking Up Notice */}
        {isWakingUp && (
          <div
            className="mb-8 p-4 rounded-lg bg-amber-950/40 border border-amber-800/60 text-amber-200 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4"
            role="status"
            aria-live="polite"
            data-testid="backend-waking-banner"
          >
            <div>
              <h3 className="font-semibold text-amber-100 flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-amber-400 animate-pulse" />
                Backend Waking Up
              </h3>
              <p className="text-sm text-amber-300 mt-1">
                The free hosting service spins down during periods of inactivity and may take 30–50 seconds to start. Reconnecting automatically...
              </p>
            </div>
            <div className="text-xs font-mono text-amber-400 flex items-center gap-1.5 self-end sm:self-auto">
              <span className="w-3.5 h-3.5 border-2 border-amber-400 border-t-transparent rounded-full animate-spin" />
              <span>Waiting for server</span>
            </div>
          </div>
        )}

        {/* Backend Connection Warning Banner */}
        {!isWakingUp && error && (
          <div
            className="mb-8 p-4 rounded-lg bg-red-950/40 border border-red-800/60 text-red-200 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4"
            role="alert"
          >
            <div>
              <h3 className="font-semibold text-red-100 flex items-center gap-2">
                <span>⚠</span> Backend Connection Failed
              </h3>
              <p className="text-sm text-red-300 mt-1">
                {error.message} (Status {error.status || 'Network Error'}). Unable to reach the backend service. Please check your connection or retry.
              </p>
            </div>
            <button
              onClick={refetch}
              className="px-3 py-1.5 rounded bg-red-800 hover:bg-red-700 text-xs font-medium text-white transition-colors"
            >
              Retry Connection
            </button>
          </div>
        )}

        {/* Tab Views */}
        {currentTab === 'dashboard' && (
          <DashboardView
            health={health}
            isLoadingHealth={isLoading}
            onNavigateToCurriculum={() => setCurrentTab('curriculum')}
            onResumeSession={handleSessionStartedOrResumed}
          />
        )}

        {currentTab === 'curriculum' && (
          <CurriculumView
            health={health}
            onSessionStarted={handleSessionStartedOrResumed}
          />
        )}

        {currentTab === 'learning' && (
          <section className="space-y-6">
            <div>
              <h1 className="text-2xl font-bold text-slate-50 tracking-tight">Learning Workspace</h1>
              <p className="text-slate-400 mt-1">
                Active pedagogical learning environment.
              </p>
            </div>

            {activeSession ? (
              <LearningWorkspace
                session={activeSession}
                onNavigateToCurriculum={() => setCurrentTab('curriculum')}
                onNavigateToDashboard={() => setCurrentTab('dashboard')}
              />
            ) : (
              <div className="p-8 rounded-lg bg-slate-800/40 border border-slate-700 text-center space-y-3">
                <p className="text-sm text-slate-400">
                  No active session selected.
                </p>
                <button
                  onClick={() => setCurrentTab('curriculum')}
                  className="px-4 py-2 rounded bg-sky-600 hover:bg-sky-500 text-sm font-medium text-white transition-colors"
                >
                  Choose a Topic from Curriculum
                </button>
              </div>
            )}
          </section>
        )}

        {currentTab === 'progress' && (
          <ProgressView
            onNavigateToCurriculum={() => setCurrentTab('curriculum')}
            onResumeSession={handleSessionStartedOrResumed}
          />
        )}

        {currentTab === 'settings' && (
          <SettingsView
            onResumeSession={handleSessionStartedOrResumed}
          />
        )}
      </main>

      <footer className="bg-slate-900 border-t border-slate-800 py-4 text-center text-xs text-slate-500 font-mono">
        Java DSA Mastery &bull; Open-Source &bull; ₹0 Budget Target &bull; Capability-Based Identity
      </footer>
    </div>
  );
}

export default App;
