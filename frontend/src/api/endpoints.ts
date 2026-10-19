/**
 * Centralized API endpoints for the Java DSA Mastery backend.
 */
export const ENDPOINTS = {
  health: () => '/health',
  curriculum: () => '/curriculum',
  sessions: () => '/sessions',
  session: (sessionId: string) => `/sessions/${encodeURIComponent(sessionId)}`,
  currentQuestion: (sessionId: string) => `/sessions/${encodeURIComponent(sessionId)}/current-question`,
  requestHint: (sessionId: string) => `/sessions/${encodeURIComponent(sessionId)}/request-hint`,
  submitAnswer: (sessionId: string) => `/sessions/${encodeURIComponent(sessionId)}/submit-answer`,
} as const;
