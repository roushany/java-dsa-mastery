import React, { useState } from 'react';
import { useLearningSession } from '../../hooks/useLearningSession';
import { ApiClient, defaultApiClient } from '../../api/client';
import { SessionResponse } from '../../api/types';

interface LearningWorkspaceProps {
  session: SessionResponse;
  client?: ApiClient;
  onNavigateToCurriculum: () => void;
  onNavigateToDashboard: () => void;
}

export const LearningWorkspace: React.FC<LearningWorkspaceProps> = ({
  session,
  client = defaultApiClient,
  onNavigateToCurriculum,
  onNavigateToDashboard,
}) => {
  const {
    question,
    isLoadingQuestion,
    questionError,
    hints,
    isRequestingHint,
    hintError,
    isSubmittingAnswer,
    submitError,
    evaluationResult,
    rateLimitCountdown,
    loadCurrentQuestion,
    requestHint,
    submitAnswer,
    advanceToNextQuestion,
  } = useLearningSession(session.sessionId, client);

  const [answerText, setAnswerText] = useState<string>('');

  const isCodeQuestion =
    question?.stage === 'IMPLEMENTATION' || question?.stage === 'DEBUGGING';

  const charCount = answerText.length;
  const isOverLimit = charCount > 2000;
  const isNearLimit = charCount > 1900;
  const isSessionExpired =
    submitError?.isNotFound || submitError?.isSessionExpired || false;
  const isSubmittable =
    charCount > 0 &&
    !isOverLimit &&
    !isSubmittingAnswer &&
    rateLimitCountdown === null &&
    !question?.isTerminal &&
    !isSessionExpired;

  const handleTextChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    // Hard-cap at 2000 characters to conform to backend validation
    if (e.target.value.length <= 2000) {
      setAnswerText(e.target.value);
    }
  };

  const handleAnswerSubmit = async () => {
    if (!isSubmittable) {
      return;
    }
    const res = await submitAnswer(answerText);
    if (res && res.stageAdvanced) {
      // Clear answer text for the next stage once advanced
      setAnswerText('');
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
      e.preventDefault();
      handleAnswerSubmit();
    }
  };

  const getDifficultyBadge = (difficulty: string | null) => {
    switch (difficulty?.toUpperCase()) {
      case 'EASY':
      case 'BEGINNER':
        return 'bg-emerald-950/60 text-emerald-300 border-emerald-800/60';
      case 'MEDIUM':
      case 'INTERMEDIATE':
        return 'bg-sky-950/60 text-sky-300 border-sky-800/60';
      case 'HARD':
      case 'ADVANCED':
        return 'bg-amber-950/60 text-amber-300 border-amber-800/60';
      default:
        return 'bg-slate-800 text-slate-400 border-slate-700';
    }
  };

  const formatStageTitle = (stage: string) => {
    return stage
      .replace(/_/g, ' ')
      .toLowerCase()
      .replace(/\b\w/g, (c) => c.toUpperCase());
  };

  return (
    <div className="space-y-6">
      {/* Session Breadcrumb & Navigation Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-4 border-b border-slate-800">
        <div className="flex items-center gap-3">
          <button
            onClick={onNavigateToCurriculum}
            className="text-xs text-sky-400 hover:text-sky-300 font-medium transition-colors"
          >
            &larr; Curriculum
          </button>
          <span className="text-slate-600">/</span>
          <span className="text-xs text-slate-400 font-mono">
            {session.topicTitle}
          </span>
          <span className="text-slate-600">/</span>
          <span className="text-xs font-mono font-medium text-slate-200">
            {question ? formatStageTitle(question.stage) : formatStageTitle(session.currentState)}
          </span>
        </div>

        <div className="flex items-center gap-3 text-xs font-mono">
          <button
            onClick={onNavigateToDashboard}
            className="text-slate-400 hover:text-slate-200 transition-colors"
          >
            Dashboard
          </button>
        </div>
      </div>

      {/* Session Header Card */}
      <div
        className="bg-slate-800/80 border border-slate-700 rounded-lg p-6 space-y-4"
        data-testid="active-learning-session"
      >
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 pb-4 border-b border-slate-700">
          <div>
            <span className="text-xs font-mono uppercase tracking-wider text-sky-400 font-semibold">
              Active Topic
            </span>
            <h2 className="text-xl font-bold text-slate-100">{session.topicTitle}</h2>
          </div>
          <div className="flex items-center gap-3 font-mono text-xs">
            <span className="px-2.5 py-1 rounded bg-slate-900 border border-slate-700 text-slate-300">
              Stage: <strong className="text-slate-100">{question?.stage || session.currentState}</strong>
            </span>
            <span className="px-2.5 py-1 rounded bg-slate-900 border border-slate-700 text-slate-300">
              Mastery: <strong className="text-slate-100">{evaluationResult?.currentMastery || session.mastery}</strong>
            </span>
          </div>
        </div>

        <div className="text-xs text-slate-400 font-mono">
          Session ID: {session.sessionId}
        </div>
      </div>

      {/* Question Loading State */}
      {isLoadingQuestion && (
        <div
          className="p-16 rounded-lg bg-slate-800/30 border border-slate-700/60 text-center space-y-3"
          data-testid="workspace-loading"
        >
          <div className="w-8 h-8 mx-auto border-2 border-sky-500 border-t-transparent rounded-full animate-spin" />
          <p className="text-sm font-medium text-slate-300">
            Loading active pedagogical question...
          </p>
        </div>
      )}

      {/* Question Load Error State */}
      {!isLoadingQuestion && questionError && (
        <div
          className="p-6 rounded-lg bg-red-950/40 border border-red-800/60 space-y-4"
          role="alert"
          data-testid="question-error"
        >
          <div>
            <h2 className="text-base font-semibold text-red-100 flex items-center gap-2">
              <span>⚠</span> Unable to load question
            </h2>
            <p className="text-sm text-red-300 mt-1">{questionError.message}</p>
          </div>

          <div className="flex items-center gap-3">
            {questionError.isNotFound || questionError.isSessionExpired ? (
              <button
                onClick={onNavigateToDashboard}
                className="px-4 py-2 rounded-md bg-slate-700 hover:bg-slate-600 text-xs font-medium text-white transition-colors"
              >
                Return to Dashboard
              </button>
            ) : (
              <button
                onClick={loadCurrentQuestion}
                className="px-4 py-2 rounded-md bg-red-800 hover:bg-red-700 text-xs font-medium text-white transition-colors"
              >
                Retry Loading
              </button>
            )}
          </div>
        </div>
      )}

      {/* Terminal Session State (Completed or Failed) */}
      {!isLoadingQuestion && question && question.isTerminal && (
        <div
          className="p-10 rounded-lg bg-slate-800/70 border border-slate-700 text-center space-y-6"
          data-testid="terminal-session"
        >
          {question.stage === 'COMPLETED' ? (
            <div className="space-y-4">
              <div className="w-12 h-12 mx-auto rounded-full bg-emerald-950 border border-emerald-700 flex items-center justify-center text-emerald-400 text-xl font-bold">
                ✓
              </div>
              <h2 className="text-2xl font-bold text-slate-50">
                Topic Completed!
              </h2>
              <p className="text-slate-300 max-w-lg mx-auto text-sm">
                Congratulations! You have demonstrated verified mastery across the pedagogical stages of <strong>{session.topicTitle}</strong>.
              </p>
            </div>
          ) : (
            <div className="space-y-4">
              <div className="w-12 h-12 mx-auto rounded-full bg-amber-950 border border-amber-700 flex items-center justify-center text-amber-400 text-xl font-bold">
                ⏸
              </div>
              <h2 className="text-2xl font-bold text-slate-50">
                Session Paused: Maximum Attempts Reached
              </h2>
              <p className="text-slate-300 max-w-lg mx-auto text-sm">
                To prevent cognitive frustration, the system has paused this session after repeated attempts without stage advancement. We encourage reviewing foundational concepts or exploring a prerequisite topic before trying again.
              </p>
            </div>
          )}

          <div className="flex justify-center gap-4 pt-4">
            <button
              onClick={onNavigateToCurriculum}
              className="px-5 py-2.5 rounded-lg bg-sky-600 hover:bg-sky-500 text-white font-medium text-sm transition-colors shadow-sm"
            >
              Browse Curriculum
            </button>
            <button
              onClick={onNavigateToDashboard}
              className="px-5 py-2.5 rounded-lg bg-slate-700 hover:bg-slate-600 text-slate-200 font-medium text-sm transition-colors"
            >
              View Dashboard
            </button>
          </div>
        </div>
      )}

      {/* Active Interactive Learning Workspace */}
      {!isLoadingQuestion && question && !question.isTerminal && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6" data-testid="active-workspace">
          {/* Main Column: Question Prompt & Answer Input (2 Cols on lg) */}
          <div className="lg:col-span-2 space-y-6">
            {/* Question Prompt Card */}
            <div className="bg-slate-800/80 border border-slate-700 rounded-lg p-6 space-y-4">
              <div className="flex flex-wrap items-center justify-between gap-2 pb-3 border-b border-slate-700/60">
                <div className="flex items-center gap-2">
                  <span className="text-xs font-mono font-semibold uppercase tracking-wider text-sky-400">
                    Stage: {formatStageTitle(question.stage)}
                  </span>
                </div>
                {question.difficulty && (
                  <span
                    className={`text-xs font-mono px-2 py-0.5 rounded-full border ${getDifficultyBadge(
                      question.difficulty
                    )}`}
                  >
                    {question.difficulty}
                  </span>
                )}
              </div>

              {/* Prompt Content */}
              <div
                className="text-base text-slate-100 leading-relaxed font-sans"
                data-testid="question-content"
              >
                {question.content}
              </div>

              {/* Question-Type Honesty Box for Implementation Questions */}
              {isCodeQuestion && (
                <div
                  className="p-3 rounded bg-amber-950/30 border border-amber-800/40 text-xs text-amber-300/90 font-sans space-y-1"
                  data-testid="code-honesty-notice"
                >
                  <p className="font-semibold text-amber-200">
                    Implementation Evaluation Notice:
                  </p>
                  <p>
                    Submitted Java code is not compiled or executed in an isolated sandbox, and automated unit-test verification is currently unavailable. Implementation submissions currently transition to the debugging stage for pedagogical review rather than receiving a verified code pass. (This limitation applies specifically to implementation code verification; conceptual question evaluations remain active.)
                  </p>
                </div>
              )}
            </div>

            {/* Answer Submission Error Alert */}
            {submitError && (
              <div
                className="p-4 rounded-lg bg-red-950/40 border border-red-800/60 text-sm text-red-200 space-y-3"
                role="alert"
                data-testid="submit-error"
              >
                <div className="space-y-1">
                  <div className="font-semibold text-red-100 flex items-center gap-1.5">
                    <span>⚠</span>{' '}
                    {submitError.isNotFound || submitError.isSessionExpired
                      ? 'Session Expired'
                      : 'Submission Error'}
                  </div>
                  <p>
                    {submitError.isNotFound || submitError.isSessionExpired
                      ? 'Your learning session has expired or is no longer available on the server. Please return to the dashboard to select a topic and begin a new session.'
                      : submitError.message}
                  </p>
                  {rateLimitCountdown !== null && (
                    <p className="font-mono text-xs text-red-300">
                      Rate limited: please wait {rateLimitCountdown}s before retrying.
                    </p>
                  )}
                </div>

                {(submitError.isNotFound || submitError.isSessionExpired) && (
                  <div>
                    <button
                      type="button"
                      onClick={onNavigateToDashboard}
                      className="px-4 py-2 rounded-md bg-slate-700 hover:bg-slate-600 text-xs font-medium text-white transition-colors"
                      data-testid="btn-expired-return-dashboard"
                    >
                      Return to Dashboard
                    </button>
                  </div>
                )}
              </div>
            )}

            {/* Evaluation Feedback Card */}
            {evaluationResult && (
              <div
                className={`p-6 rounded-lg border space-y-4 ${
                  evaluationResult.evaluation === 'PASS'
                    ? 'bg-emerald-950/30 border-emerald-700/60 text-emerald-100'
                    : 'bg-amber-950/30 border-amber-700/60 text-amber-100'
                }`}
                role="status"
                data-testid="evaluation-feedback"
              >
                <div className="flex items-center justify-between gap-4">
                  <div className="flex items-center gap-2">
                    <span
                      className={`text-lg font-bold ${
                        evaluationResult.evaluation === 'PASS'
                          ? 'text-emerald-400'
                          : 'text-amber-400'
                      }`}
                    >
                      {evaluationResult.evaluation === 'PASS' ? '✓' : '⚠'}
                    </span>
                    <h3 className="font-semibold text-base">
                      {evaluationResult.evaluation === 'PASS'
                        ? 'Concept Verified (PASS)'
                        : `Evaluation Result: ${evaluationResult.evaluation}`}
                    </h3>
                  </div>

                  {evaluationResult.stageAdvanced && (
                    <span className="text-xs font-mono px-2 py-0.5 rounded bg-emerald-900/60 border border-emerald-700 text-emerald-200">
                      Stage Advanced &rarr; {evaluationResult.currentState}
                    </span>
                  )}
                </div>

                <p className="text-sm leading-relaxed">{evaluationResult.feedback}</p>

                {evaluationResult.misconceptionDetected && (
                  <div className="text-xs font-mono p-2.5 rounded bg-slate-900/60 border border-slate-800 text-amber-300">
                    <span className="text-slate-400">Identified Misconception: </span>
                    <strong>{evaluationResult.misconceptionDetected}</strong>
                  </div>
                )}

                <div className="pt-2 flex items-center gap-3">
                  {evaluationResult.stageAdvanced ? (
                    <button
                      onClick={advanceToNextQuestion}
                      className="px-4 py-2 rounded-md bg-emerald-700 hover:bg-emerald-600 text-white text-xs font-medium transition-colors"
                      data-testid="btn-next-question"
                    >
                      Continue to Next Stage &rarr;
                    </button>
                  ) : (
                    <button
                      onClick={advanceToNextQuestion}
                      className="px-4 py-2 rounded-md bg-slate-700 hover:bg-slate-600 text-slate-200 text-xs font-medium transition-colors"
                      data-testid="btn-next-question"
                    >
                      Try Next Question
                    </button>
                  )}
                </div>
              </div>
            )}

            {/* Answer Input Area */}
            <div className="bg-slate-800/80 border border-slate-700 rounded-lg p-5 space-y-3">
              <div className="flex items-center justify-between">
                <label
                  htmlFor="answer-input"
                  className="text-xs font-semibold uppercase tracking-wider font-mono text-slate-300"
                >
                  {isCodeQuestion ? 'Java Method Implementation' : 'Your Explanation'}
                </label>
                <span
                  className={`text-xs font-mono ${
                    isNearLimit ? 'text-amber-400 font-semibold' : 'text-slate-400'
                  }`}
                  data-testid="char-counter"
                >
                  {charCount} / 2000
                </span>
              </div>

              <textarea
                id="answer-input"
                value={answerText}
                onChange={handleTextChange}
                onKeyDown={handleKeyDown}
                disabled={isSubmittingAnswer || rateLimitCountdown !== null || isSessionExpired}
                rows={isCodeQuestion ? 8 : 5}
                placeholder={
                  isCodeQuestion
                    ? '// Write your Java implementation method here...'
                    : 'Explain the core algorithmic principle in your own words...'
                }
                className={`w-full p-3 rounded-md bg-slate-950 border text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-sky-500 transition-colors ${
                  isCodeQuestion ? 'font-mono text-xs' : 'font-sans'
                } ${isNearLimit ? 'border-amber-600' : 'border-slate-700'}`}
                aria-describedby="char-counter"
              />

              <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 pt-2">
                <span className="text-xs text-slate-500 font-mono">
                  Pro-tip: Press <kbd className="px-1.5 py-0.5 rounded bg-slate-700 text-slate-300">Ctrl+Enter</kbd> to submit
                </span>

                <button
                  onClick={handleAnswerSubmit}
                  disabled={!isSubmittable}
                  className={`px-5 py-2 rounded-md text-xs font-medium transition-colors flex items-center justify-center gap-2 ${
                    isSubmittingAnswer
                      ? 'bg-sky-600/50 text-white cursor-wait'
                      : !isSubmittable
                      ? 'bg-slate-700 text-slate-500 cursor-not-allowed'
                      : 'bg-sky-600 hover:bg-sky-500 text-white shadow-sm'
                  }`}
                  data-testid="btn-submit-answer"
                >
                  {isSubmittingAnswer ? (
                    <>
                      <span className="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin" />
                      Evaluating Answer...
                    </>
                  ) : rateLimitCountdown !== null ? (
                    `Wait ${rateLimitCountdown}s...`
                  ) : (
                    'Submit Answer'
                  )}
                </button>
              </div>
            </div>
          </div>

          {/* Side Column: Progressive Hints Drawer */}
          <div className="space-y-4">
            <div className="bg-slate-800/80 border border-slate-700 rounded-lg p-5 space-y-4">
              <div className="flex items-center justify-between pb-3 border-b border-slate-700">
                <h3 className="text-sm font-semibold text-slate-100 tracking-tight">
                  Progressive Hints
                </h3>
                <span className="text-xs font-mono text-slate-400">
                  {hints.length} revealed
                </span>
              </div>

              {/* Hint Error Alert */}
              {hintError && (
                <div
                  className="p-3 rounded bg-red-950/40 border border-red-800/60 text-xs text-red-200"
                  role="alert"
                  data-testid="hint-error"
                >
                  <p className="font-semibold text-red-100">Failed to get hint</p>
                  <p className="mt-1">{hintError.message}</p>
                </div>
              )}

              {/* Hint List */}
              {hints.length === 0 ? (
                <p className="text-xs text-slate-400 italic">
                  No hints requested yet. If you are stuck, request a hint for incremental guidance.
                </p>
              ) : (
                <div className="space-y-3" data-testid="hints-list">
                  {hints.map((h, idx) => (
                    <div
                      key={idx}
                      className="p-3 rounded bg-slate-900/80 border border-slate-700/60 text-xs space-y-1.5"
                      data-testid={`hint-item-${idx}`}
                    >
                      <div className="font-mono text-sky-400 font-semibold">
                        Hint Level {h.hintLevel}:
                      </div>
                      <p className="text-slate-300 leading-relaxed">{h.hintText}</p>
                    </div>
                  ))}
                </div>
              )}

              {/* Request Hint Action */}
              <button
                onClick={requestHint}
                disabled={isRequestingHint || rateLimitCountdown !== null}
                className={`w-full py-2 px-3 rounded text-xs font-medium transition-colors flex items-center justify-center gap-2 ${
                  isRequestingHint
                    ? 'bg-slate-700 text-slate-400 cursor-wait'
                    : rateLimitCountdown !== null
                    ? 'bg-slate-700 text-slate-500 cursor-not-allowed'
                    : 'bg-slate-700 hover:bg-slate-600 text-slate-200'
                }`}
                data-testid="btn-request-hint"
              >
                {isRequestingHint ? (
                  <>
                    <span className="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin" />
                    Requesting Hint...
                  </>
                ) : rateLimitCountdown !== null ? (
                  `Rate Limited (${rateLimitCountdown}s)`
                ) : (
                  `Request Hint ${hints.length + 1}`
                )}
              </button>

              <p className="text-[11px] text-slate-500 leading-normal">
                Pedagogical note: Hints provide guidance without penalizing completion, but are recorded in evidence history.
              </p>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
