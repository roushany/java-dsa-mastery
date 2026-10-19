/**
 * TypeScript API definitions mirroring Spring Boot backend DTOs and models.
 * Grounded in com.javadsamastery.dto.* and com.javadsamastery.domain.*
 */

export type LearningState =
  | 'CONTENT_INTRO'
  | 'CONCEPT_ASSESSMENT'
  | 'REMEDIATION'
  | 'DERIVE'
  | 'IMPLEMENTATION'
  | 'DEBUGGING'
  | 'EXPLANATION'
  | 'COUNTEREXAMPLE'
  | 'OPTIMIZE'
  | 'TRANSFER'
  | 'RETRIEVAL'
  | 'COMPLETED'
  | 'FAILED_TERMINATED'
  | 'SOLUTION_WALKTHROUGH';

export type TopicMastery = 'NOVICE' | 'FAMILIAR' | 'PROFICIENT' | 'MASTERED';

export type EvaluationResult =
  | 'PASS'
  | 'FAIL'
  | 'PARTIAL'
  | 'HINT'
  | 'INVALID_RESPONSE'
  | 'PROVIDER_FAILURE'
  | 'PROVIDER_TIMEOUT';

export interface HealthResponse {
  status: 'UP' | 'DOWN';
  version: string;
  activeTopics: number;
  aiConfigured: boolean;
}

export interface TopicSummaryDto {
  topicId: string;
  title: string;
  difficulty: string;
  prerequisites: string[];
  hasContent: boolean;
  totalStages: number;
}

export interface CurriculumResponse {
  topics: TopicSummaryDto[];
}

export interface StartSessionRequest {
  topicId: string;
  learnerId?: string;
}

export interface SessionResponse {
  sessionId: string;
  learnerId: string;
  topicId: string;
  topicTitle: string;
  currentState: LearningState;
  mastery: TopicMastery;
  totalStages: number;
  completed: boolean;
}

export interface QuestionResponse {
  attemptId: string | null;
  sessionId: string;
  topicId: string;
  stage: string;
  difficulty: string | null;
  content: string;
  hintsUsed: number;
  isTerminal: boolean;
}

export interface RequestHintRequest {
  attemptId: string;
}

export interface HintResponse {
  attemptId: string;
  hintLevel: number;
  hintText: string;
}

export interface SubmitAnswerRequest {
  attemptId: string;
  answer: string;
  /** @deprecated Untrusted client data, ignored by backend */
  codeTestsPassed?: boolean | null;
}

export interface SubmitAnswerResponse {
  attemptId: string;
  evaluation: EvaluationResult;
  feedback: string;
  misconceptionDetected: string | null;
  currentState: LearningState;
  currentMastery: TopicMastery;
  stageAdvanced: boolean;
  hintsUsed: number;
}

export interface ErrorResponse {
  error: string;
  message: string;
  retryable: boolean;
  timestamp: number;
}
