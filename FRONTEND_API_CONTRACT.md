# Frontend API Contract Specification

**Project:** Java DSA Mastery
**Phase:** 3A (Frontend Architecture & API Contract)
**Backend Reference:** Spring Boot REST API (`com.javadsamastery.controller`, `com.javadsamastery.dto`)
**Specification Version:** 1.0.0
**Target Client:** React + TypeScript (Vite)

---

## 1. Overview & Transport Protocol

The Java DSA Mastery backend provides a clean RESTful HTTP API under the base path `/api/v1`.

### Base URL Configuration
- **Local Development:** Proxied via Vite dev server (`http://localhost:5173/api/v1` -> `http://localhost:8080/api/v1`) to prevent CORS overhead during development.
- **Production / Standalone:** Configured via browser environment variable `VITE_API_BASE_URL` (defaults to `/api/v1` when served from the same domain/reverse proxy).

### CORS Policy (Verified from `WebSecurityConfig.java`)
- **Allowed Origins:** Configured via `javadsa.security.cors.allowed-origins`. Defaults to `http://localhost:3000,http://localhost:5173`. Vite's standard port `5173` is supported out of the box.
- **Allowed Methods:** `GET`, `POST`, `OPTIONS`.
- **Allowed Headers:** `Content-Type`, `X-Learner-Token`, `Authorization`.
- **Exposed Headers:** `X-Learner-Token`, `Retry-After`. The browser JavaScript runtime can read both headers.
- **Credentials:** `allowCredentials: false`. Authentication is stateless and capability-based via HTTP headers; cookies are not utilized.

---

## 2. Learner Identity & Capability Token Contract

The backend uses a lightweight cryptographic capability token instead of a traditional username/password authentication scheme (`LearnerTokenService.java`, `SessionSecurityInterceptor.java`).

### Token Structure
A token string consists of three dot-separated components:
```
<learnerId>.<timestampMs>.<base64url-hmac-sha256>
```
1. `learnerId`: Alphanumeric identifier, e.g. `learner-a1b2c3d4` or `student-alice` (`^[a-zA-Z0-9_-]{1,64}$`).
2. `timestampMs`: Epoch millisecond timestamp of token issuance.
3. `base64url-hmac-sha256`: Cryptographic HMAC-SHA256 signature calculated over `<learnerId>.<timestampMs>` using the server's private secret (`javadsa.security.token.secret`).

### Token Lifecycle & Rules
1. **Initial Bootstrap (Anonymous):**
   - The frontend issues `POST /api/v1/sessions` with `topicId` and **no** `X-Learner-Token` header.
   - The server mints an anonymous identifier (`learner-<uuid8>`), signs a new token, and returns it in the HTTP response header `X-Learner-Token`.
   - The frontend intercepts this header and stores it in `localStorage` under key `javadsa_learner_token`.
2. **Subsequent API Invocations:**
   - Every subsequent request to `/api/v1/sessions/**` **must** include the header `X-Learner-Token: <token>`.
   - Missing, malformed, tampered, or expired tokens result in `401 Unauthorized`.
3. **Session Ownership Enforcement:**
   - On protected session endpoints (`/api/v1/sessions/{sessionId}/**`), the server verifies that the `learnerId` cryptographically derived from `X-Learner-Token` matches the `learnerId` recorded on the session.
   - If a caller presents a valid token for `learner-bob` while accessing a session owned by `learner-alice`, the server returns `403 Forbidden` (`FORBIDDEN`).
4. **Token Expiration:**
   - Default validity: 30 days (`javadsa.security.token.validity-days`).
   - Clock skew tolerance: Future timestamps up to 60 seconds are permitted. Tokens older than 30 days are rejected as invalid.

---

## 3. Global Error Contract

All controller exception handlers in `GlobalExceptionHandler.java` serialize errors into a standardized `ErrorResponse` JSON structure:

### Error Response Schema
```typescript
interface ErrorResponse {
  error: string;       // Stable machine-readable error code
  message: string;     // Human-readable diagnostic description
  retryable: boolean;  // True if client should offer retry
  timestamp: number;   // Epoch millisecond timestamp
}
```

### Complete HTTP Status & Error Code Mapping Table

| HTTP Status | Error Code (`error`) | Description | `retryable` | Client Handling |
| :--- | :--- | :--- | :---: | :--- |
| **400 Bad Request** | `VALIDATION_ERROR` | Request body validation failed (`@NotBlank`, regex, size limits). | `false` | Highlight form field validation errors. |
| **400 Bad Request** | `INVALID_TOPIC` | Requested `topicId` is not in curriculum. | `false` | Redirect user back to curriculum selection. |
| **400 Bad Request** | `INVALID_ATTEMPT` | Attempt ID is not the active attempt for the session. | `false` | Re-fetch current question via `GET /current-question`. |
| **400 Bad Request** | `BAD_REQUEST` | Illegal argument or illegal state. | `false` | Display error alert. |
| **401 Unauthorized** | `UNAUTHORIZED` | Missing, malformed, or expired `X-Learner-Token`. | `false` | Clear cached token; restart session as new learner. |
| **403 Forbidden** | `FORBIDDEN` | Cross-learner access attempt or unauthenticated client-selected learner ID. | `false` | Prevent session access; prompt user to switch profile. |
| **404 Not Found** | `SESSION_NOT_FOUND` | Session ID does not exist in cache or database. | `false` | Inform user session was not found; return to Home. |
| **404 Not Found** | `NOT_FOUND` | Route or resource does not exist. | `false` | Show 404 page. |
| **409 Conflict** | `DUPLICATE_ATTEMPT` | Attempt already consumed with a different submission. | `false` | Fetch active question; cannot alter submitted attempt. |
| **409 Conflict** | `STALE_SESSION_VERSION` | Optimistic locking collision during state update. | `false` | Auto-refresh session status. |
| **410 Gone** | `SESSION_EXPIRED` | Session exceeded TTL (24h default). | `false` | Inform student session expired; offer "Start New Session". |
| **429 Too Many Requests** | `RATE_LIMIT_EXCEEDED` | Exceeded general IP limit or AI quota. | `true` | Read `Retry-After` header, disable submit button, show countdown. |
| **500 Internal Error** | `INTERNAL_SERVER_ERROR` | Unhandled server exception. | `true` | Show retry prompt; log details to console. |
| **503 Service Unavailable** | `PROVIDER_UNAVAILABLE` | Gemini/LLM service unreachable or downstream DB down. | `true` | Display banner: "AI Tutor temporarily offline; retry in a moment". |
| **504 Gateway Timeout** | `PROVIDER_TIMEOUT` | Gemini/LLM call exceeded timeout threshold (8s). | `true` | Offer retry without burning attempt. |

---

## 4. Detailed Endpoint Specifications

### 4.1 System Health
```http
GET /api/v1/health
```
- **Description:** Checks application liveness, database connectivity, and AI provider configuration status.
- **Security:** Public. No `X-Learner-Token` required.
- **Rate Limit:** General IP bucket (300 RPM).

#### Response: `200 OK` (Healthy)
```json
{
  "status": "UP",
  "version": "1.0.0",
  "activeTopics": 7,
  "aiConfigured": false
}
```

#### Response: `503 Service Unavailable` (Database Down)
```json
{
  "status": "DOWN",
  "version": "1.0.0",
  "activeTopics": 7,
  "aiConfigured": false
}
```

#### TypeScript Type
```typescript
interface HealthResponse {
  status: "UP" | "DOWN";
  version: string;
  activeTopics: number;
  aiConfigured: boolean; // True if GEMINI_API_KEY is configured on server
}
```

---

### 4.2 Curriculum Overview
```http
GET /api/v1/curriculum
```
- **Description:** Returns all implemented DSA topics with prerequisite linkages, difficulty tiers, and stage counts.
- **Security:** Public. No `X-Learner-Token` required.
- **Rate Limit:** General IP bucket (300 RPM).

#### Response: `200 OK`
```json
{
  "topics": [
    {
      "topicId": "linear-search",
      "title": "Linear Search",
      "difficulty": "BEGINNER",
      "prerequisites": ["strings"],
      "hasContent": true,
      "totalStages": 1
    },
    {
      "topicId": "binary-search",
      "title": "Binary Search",
      "difficulty": "BEGINNER",
      "prerequisites": ["linear-search"],
      "hasContent": true,
      "totalStages": 9
    },
    {
      "topicId": "binary-search-on-answer",
      "title": "Binary Search on Answer",
      "difficulty": "INTERMEDIATE",
      "prerequisites": ["binary-search"],
      "hasContent": true,
      "totalStages": 9
    },
    {
      "topicId": "two-pointers",
      "title": "Two Pointers",
      "difficulty": "INTERMEDIATE",
      "prerequisites": ["binary-search"],
      "hasContent": true,
      "totalStages": 9
    },
    {
      "topicId": "prefix-sum",
      "title": "Prefix Sum",
      "difficulty": "INTERMEDIATE",
      "prerequisites": ["linear-search"],
      "hasContent": true,
      "totalStages": 9
    },
    {
      "topicId": "sliding-window",
      "title": "Sliding Window",
      "difficulty": "INTERMEDIATE",
      "prerequisites": ["two-pointers"],
      "hasContent": true,
      "totalStages": 9
    },
    {
      "topicId": "hashing",
      "title": "Hashing",
      "difficulty": "INTERMEDIATE",
      "prerequisites": ["sliding-window"],
      "hasContent": true,
      "totalStages": 9
    }
  ]
}
```

#### TypeScript Types
```typescript
interface TopicSummary {
  topicId: string;
  title: string;
  difficulty: "BEGINNER" | "INTERMEDIATE" | "ADVANCED";
  prerequisites: string[];
  hasContent: boolean;
  totalStages: number;
}

interface CurriculumResponse {
  topics: TopicSummary[];
}
```

---

### 4.3 Start Learning Session
```http
POST /api/v1/sessions
```
- **Description:** Initializes a new interactive learning session for a specific topic.
- **Security:**
  - If `X-Learner-Token` is supplied: Must be valid; `learnerId` is derived from token. If body contains `learnerId`, it must match token.
  - If `X-Learner-Token` is omitted: Server mints an anonymous `learner-<uuid8>`. Client must NOT provide `learnerId` in body without a token (returns `403 Forbidden`).
- **Response Headers:** `X-Learner-Token: <token>` (contains issued or confirmed token).

#### Request Headers
```http
Content-Type: application/json
X-Learner-Token: learner-a1b2c3d4.1729272000000.v4l1ds1gn4tur3 (optional for anonymous start)
```

#### Request Body
```json
{
  "topicId": "binary-search",
  "learnerId": "learner-a1b2c3d4" // Optional; only allowed if token is passed and matches
}
```

#### Response: `201 Created`
```json
{
  "sessionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "learnerId": "learner-a1b2c3d4",
  "topicId": "binary-search",
  "topicTitle": "Binary Search",
  "currentState": "CONTENT_INTRO",
  "mastery": "NOVICE",
  "totalStages": 9,
  "completed": false
}
```

#### TypeScript Types
```typescript
interface StartSessionRequest {
  topicId: string;
  learnerId?: string;
}

interface SessionResponse {
  sessionId: string;
  learnerId: string;
  topicId: string;
  topicTitle: string;
  currentState: LearningState;
  mastery: TopicMastery;
  totalStages: number;
  completed: boolean;
}

type LearningState =
  | "CONTENT_INTRO"
  | "CONCEPT_ASSESSMENT"
  | "REMEDIATION"
  | "DERIVE"
  | "IMPLEMENTATION"
  | "DEBUGGING"
  | "EXPLANATION"
  | "COUNTEREXAMPLE"
  | "OPTIMIZE"
  | "TRANSFER"
  | "RETRIEVAL"
  | "COMPLETED"
  | "FAILED_TERMINATED"
  | "SOLUTION_WALKTHROUGH";

type TopicMastery = "NOVICE" | "FAMILIAR" | "PROFICIENT" | "MASTERED";
```

---

### 4.4 Get Session Details
```http
GET /api/v1/sessions/{sessionId}
```
- **Description:** Retrieves current state, mastery level, and completion status of an active session.
- **Security:** Requires `X-Learner-Token`. Caller must own the session.

#### Response: `200 OK`
```json
{
  "sessionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "learnerId": "learner-a1b2c3d4",
  "topicId": "binary-search",
  "topicTitle": "Binary Search",
  "currentState": "CONCEPT_ASSESSMENT",
  "mastery": "NOVICE",
  "totalStages": 9,
  "completed": false
}
```

---

### 4.5 Get Current Question
```http
GET /api/v1/sessions/{sessionId}/current-question
```
- **Description:** Retrieves the current active pedagogical prompt and attempt token for the session. If no active attempt exists, generates the next question according to the student's mastery and learning state.
- **Security:** Requires `X-Learner-Token`. Caller must own the session.

#### Response: `200 OK` (Active Question)
```json
{
  "attemptId": "attempt-7b1c4e90-1234",
  "sessionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "topicId": "binary-search",
  "stage": "CONCEPT_ASSESSMENT",
  "difficulty": "EASY",
  "content": "Why is it impossible to eliminate half of an unsorted array like we do in Binary Search?",
  "hintsUsed": 0,
  "isTerminal": false
}
```

#### Response: `200 OK` (Terminal State: Completed or Failed)
```json
{
  "attemptId": null,
  "sessionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "topicId": "binary-search",
  "stage": "COMPLETED",
  "difficulty": null,
  "content": "Lesson terminated: COMPLETED",
  "hintsUsed": 0,
  "isTerminal": true
}
```

#### TypeScript Types
```typescript
interface QuestionResponse {
  attemptId: string | null;
  sessionId: string;
  topicId: string;
  stage: string;
  difficulty: "EASY" | "MEDIUM" | "HARD" | null;
  content: string;
  hintsUsed: number;
  isTerminal: boolean;
}
```

---

### 4.6 Request Hint
```http
POST /api/v1/sessions/{sessionId}/request-hint
```
- **Description:** Requests a pedagogical hint for the current active attempt. Hints are generated progressively (Level 1, Level 2, etc.) and recorded against the attempt.
- **Security:** Requires `X-Learner-Token`. Caller must own the session.
- **Rate Limit:** AI composite quota (10 RPM per session, 15 RPM per learner, 60 RPM global).

#### Request Body
```json
{
  "attemptId": "attempt-7b1c4e90-1234"
}
```

#### Response: `200 OK`
```json
{
  "attemptId": "attempt-7b1c4e90-1234",
  "hintLevel": 1,
  "hintText": "Consider how binary search relies on order to determine which half cannot contain the target."
}
```

#### TypeScript Types
```typescript
interface RequestHintRequest {
  attemptId: string;
}

interface HintResponse {
  attemptId: string;
  hintLevel: number;
  hintText: string;
}
```

---

### 4.7 Submit Answer
```http
POST /api/v1/sessions/{sessionId}/submit-answer
```
- **Description:** Submits student's response for evaluation. Performs Socratic assessment, updates evidence policy, and advances or adapts the learning state.
- **Security:** Requires `X-Learner-Token`. Caller must own the session.
- **Rate Limit:** AI composite quota (10 RPM per session, 15 RPM per learner, 60 RPM global).
- **Constraints:** `answer` must be 1 to 2000 characters.

#### Request Body
```json
{
  "attemptId": "attempt-7b1c4e90-1234",
  "answer": "Binary search requires sorted elements so comparisons eliminate entire ranges. Unsorted arrays provide no such guarantee.",
  "codeTestsPassed": null // Deprecated: ignored by server
}
```

#### Response: `200 OK`
```json
{
  "attemptId": "attempt-7b1c4e90-1234",
  "evaluation": "PASS",
  "feedback": "Evaluation completed: PASS",
  "misconceptionDetected": null,
  "currentState": "DERIVE",
  "currentMastery": "NOVICE",
  "stageAdvanced": true,
  "hintsUsed": 1
}
```

#### Idempotency Behavior
- If the exact same `(attemptId, answer)` is submitted again: returns the committed evaluation (`200 OK`, `stageAdvanced: false`).
- If `attemptId` is reused with a *different* answer: returns `409 Conflict` (`DUPLICATE_ATTEMPT`).

#### TypeScript Types
```typescript
interface SubmitAnswerRequest {
  attemptId: string;
  answer: string;
  codeTestsPassed?: boolean | null; // Deprecated
}

interface SubmitAnswerResponse {
  attemptId: string;
  evaluation: "PASS" | "FAIL" | "PARTIAL" | "HINT" | "INVALID_RESPONSE";
  feedback: string;
  misconceptionDetected: string | null;
  currentState: LearningState;
  currentMastery: TopicMastery;
  stageAdvanced: boolean;
  hintsUsed: number;
}
```

---

## 5. Rate Limiting Headers & Client Strategy

The backend enforces two layers of rate limits:
1. **General Endpoints:** 300 RPM per client IP (`GET /health`, `GET /curriculum`, `POST /sessions`, `GET /sessions/**`).
2. **AI-Driven Composite Endpoints:** Enforced across global (60 RPM), learner (15 RPM), and session (10 RPM) tiers (`POST /request-hint`, `POST /submit-answer`).

### Response on 429
```http
HTTP/1.1 429 Too Many Requests
Retry-After: 6
Content-Type: application/json

{
  "error": "RATE_LIMIT_EXCEEDED",
  "message": "Session AI evaluation rate limit exceeded. Please wait before retrying.",
  "retryable": true,
  "timestamp": 1729272300000
}
```

### Client Retry Policy
1. Read `Retry-After` header (integer seconds, fallback to `5` if absent).
2. Disable "Submit Answer" and "Request Hint" buttons.
3. Show an inline countdown timer: `"Rate limit reached. Please wait X seconds..."`.
4. Automatically re-enable controls when countdown reaches 0. Do NOT hammer the server with automated polling.

---

## 6. Known Backend Boundaries & Frontend Handling

1. **Implementation Questions (`IMPLEMENTATION` / `DEBUGGING`):**
   - The server currently passes `deterministicImplementationPassed = false` into `EvidencePolicy.authorize()`.
   - In the current backend build, submitting code for `IMPLEMENTATION` questions will force an `EvaluationResult.FAIL` and transition to `DEBUGGING` because an isolated server-side code execution sandbox is not yet implemented.
   - **Frontend UI Behavior:** The frontend must clearly label code-entry questions, accept user code, display the server's evaluation result, and provide transparent guidance when entering `DEBUGGING`.
2. **Dynamic Stage Counts:**
   - In `CurriculumController`, `linear-search` returns `totalStages = 1` due to stage requirement override size logic, whereas other topics return `9`. The frontend progress bar must dynamically calculate `Math.min(stageIndex / totalStages, 1.0)` and avoid hardcoding denominator `9`.
3. **Learner Session List Endpoint Absence:**
   - The backend currently does not provide a `GET /api/v1/sessions` list endpoint.
   - **Frontend UI Behavior:** The client must persist an index of created session IDs in `localStorage` under `javadsa_sessions` (e.g., `[{ sessionId, topicId, createdAt }]`).
