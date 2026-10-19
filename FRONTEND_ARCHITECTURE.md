# Frontend Technical Architecture Specification

**Project:** Java DSA Mastery
**Phase:** 3A (Frontend Architecture & API Contract)
**Framework:** React 18 / 19 + TypeScript 5+ (Build Tool: Vite)
**Backend:** Spring Boot 3.4.2 REST API (`http://localhost:8080`)
**Architecture Style:** Feature-driven modular architecture with zero runtime CSS-in-JS bloat

---

## 1. System Context & Architectural Goals

The frontend application provides the interactive browser client for Java DSA Mastery. It communicates exclusively over HTTP with the existing Spring Boot backend.

### Architectural Principles
1. **Zero Secret Exposure:** Browser bundles and client environment variables must never contain backend secrets, database passwords, or Gemini API keys. All evaluation routing happens securely on the backend.
2. **Minimal Dependency Footprint:** Avoid bulky UI component frameworks and heavy client-side state engines. Prefer native browser capabilities (`fetch`, `localStorage`, CSS Grid/Flexbox) with lightweight typed utilities.
3. **Stateless Identity with Capability Tokens:** Employs the backend's HMAC-signed `X-Learner-Token` for cryptographic session ownership without requiring passwords or tracking cookies.
4. **Deterministic Graceful Degradation:** Robust handling of offline states, backend cold starts, rate limits (429 countdowns), and unconfigured AI modes (`aiConfigured: false`).
5. **Full Platform Portability:** Supports self-hosting, local development, and containerized deployment with zero build-time vendor lock-in.

---

## 2. Technology Stack & Toolchain

| Layer | Selected Technology | Rationale |
| :--- | :--- | :--- |
| **Language** | TypeScript 5.x | Strict type safety, shared domain schemas with backend DTOs. |
| **Runtime & Framework** | React 18 / 19 | Standard component model, declarative UI state, wide ecosystem. |
| **Build & Dev Server** | Vite 5.x / 6.x | Instant HMR, ESM native bundling, built-in development proxy. |
| **Routing** | React Router v6 / v7 | Declarative client-side routing, route loaders, and error boundaries. |
| **State Management** | Zustand (or React Context + Reducer) | Minimalist (~1KB), zero boilerplate, outside-React action dispatching. |
| **HTTP Client** | Native `fetch` wrapper | Zero external dependencies; fully typed with interceptor capabilities. |
| **Styling** | Tailwind CSS v3 / v4 | Utility-first, zero runtime CSS overhead, optimized purging for production. |
| **Syntax Highlighting** | PrismJS or lightweight CodeMirror | Monospace code block presentation without heavy IDE dependencies. |
| **Testing** | Vitest + React Testing Library + Playwright | Unified Vite toolchain for unit tests; Playwright for cross-browser E2E. |

---

## 3. Directory & Feature Structure

The project follows a **feature-based** organization where code is grouped by pedagogical domain rather than technical type:

```
frontend/
├── public/
│   ├── favicon.ico
│   └── robots.txt
├── src/
│   ├── api/                       # Centralized typed HTTP client
│   │   ├── client.ts              # Fetch wrapper with interceptors
│   │   ├── endpoints.ts           # API route constants
│   │   ├── errors.ts              # Custom ApiError class & parser
│   │   └── types.ts               # DTO interfaces mirroring backend
│   ├── assets/                    # Static assets & icons
│   ├── components/                # Shared reusable UI primitives
│   │   ├── Button/
│   │   ├── Card/
│   │   ├── Badge/
│   │   ├── Alert/
│   │   ├── ProgressBar/
│   │   ├── Modal/
│   │   └── CodeBlock/
│   ├── features/                  # Domain-driven feature modules
│   │   ├── dashboard/             # Home view & quick resume
│   │   │   ├── components/
│   │   │   └── DashboardView.tsx
│   │   ├── curriculum/            # Topic browser & graph
│   │   │   ├── components/
│   │   │   ├── hooks/useCurriculum.ts
│   │   │   └── CurriculumView.tsx
│   │   ├── session/               # Core learning workspace
│   │   │   ├── components/
│   │   │   │   ├── QuestionPrompt.tsx
│   │   │   │   ├── AnswerEditor.tsx
│   │   │   │   ├── HintAccordion.tsx
│   │   │   │   ├── FeedbackBanner.tsx
│   │   │   │   └── SessionHeader.tsx
│   │   │   ├── hooks/useSession.ts
│   │   │   ├── hooks/useQuestion.ts
│   │   │   └── SessionWorkspaceView.tsx
│   │   ├── progress/              # Mastery & history view
│   │   │   ├── components/
│   │   │   └── ProgressView.tsx
│   │   └── settings/              # Token export/import & session recovery
│   │       ├── components/
│   │       └── SettingsView.tsx
│   ├── hooks/                     # Cross-feature hooks
│   │   ├── useLearnerIdentity.ts  # Token persistence & retrieval
│   │   └── useHealthCheck.ts      # Backend connectivity poller
│   ├── layout/                    # Global shell (Navbar, Footer, MobileNav)
│   ├── store/                     # Global client stores
│   │   ├── identityStore.ts       # Active learner token & ID
│   │   └── sessionIndexStore.ts   # Local catalog of created sessions
│   ├── styles/                    # Global styles & Tailwind entry
│   │   └── main.css
│   ├── App.tsx                    # Root routing & layout shell
│   └── main.tsx                   # React root mount
├── .env.example                   # Client environment variables
├── index.html                     # HTML template
├── package.json
├── tsconfig.json
└── vite.config.ts                 # Vite config with dev proxy
```

---

## 4. API Client & Interceptor Architecture

A dedicated, lightweight client (`src/api/client.ts`) wraps `window.fetch` to enforce security headers, error parsing, and token synchronization:

```typescript
// src/api/client.ts
import { ApiError } from './errors';
import { ErrorResponse } from './types';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api/v1';
const TOKEN_STORAGE_KEY = 'javadsa_learner_token';

export async function apiClient<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const url = `${API_BASE}${endpoint}`;
  const headers = new Headers(options.headers || {});

  headers.set('Content-Type', 'application/json');

  // Inject cryptographic capability token if available
  const existingToken = localStorage.getItem(TOKEN_STORAGE_KEY);
  if (existingToken && !headers.has('X-Learner-Token')) {
    headers.set('X-Learner-Token', existingToken);
  }

  const response = await fetch(url, {
    ...options,
    headers,
  });

  // Intercept and persist newly issued or refreshed tokens
  const outgoingToken = response.headers.get('X-Learner-Token');
  if (outgoingToken) {
    localStorage.setItem(TOKEN_STORAGE_KEY, outgoingToken);
  }

  // Handle successful responses
  if (response.ok) {
    if (response.status === 204) {
      return {} as T;
    }
    return response.json() as Promise<T>;
  }

  // Parse structured ErrorResponse from GlobalExceptionHandler
  let errorData: ErrorResponse;
  try {
    errorData = await response.json();
  } catch {
    errorData = {
      error: 'HTTP_' + response.status,
      message: response.statusText || 'Unknown server error',
      retryable: response.status >= 500,
      timestamp: Date.now(),
    };
  }

  // Extract Retry-After header if present on 429
  const retryAfterHeader = response.headers.get('Retry-After');
  const retryAfterSeconds = retryAfterHeader ? parseInt(retryAfterHeader, 10) : undefined;

  throw new ApiError(response.status, errorData, retryAfterSeconds);
}
```

---

## 5. Learner Identity & State Management

Because the backend is capability-based and does not yet expose a `GET /learners/me/sessions` listing endpoint, the client maintains state in a lightweight store with local persistence:

### Store Responsibilities
1. **`identityStore`:**
   - Holds `token: string | null` and derived `learnerId: string | null`.
   - On initialization: reads `javadsa_learner_token` from `localStorage`. Extracts `learnerId` via payload split (`token.split('.')[0]`).
   - Supports: `setToken(token)`, `clearIdentity()`, `importToken(token)`.
2. **`sessionIndexStore`:**
   - Holds `recentSessions: Array<{ sessionId: string, topicId: string, lastState: string, lastMastery: string, updatedAt: number }>`.
   - Persisted in `localStorage` under `javadsa_sessions`.
   - Updated whenever `POST /sessions` succeeds or `submitAnswer` updates `currentState` or `mastery`.
   - Used by Dashboard to offer "Resume Active Session".

---

## 6. HTTP Status & Exception Handling Matrix

The frontend handles errors according to backend semantics:

```
[API Request]
      │
      ▼
[Response Status?]
      ├── 200/201: Update local state; persist X-Learner-Token.
      ├── 400 (VALIDATION_ERROR): Display field validation errors on form.
      ├── 400 (INVALID_ATTEMPT): Auto-invoke GET /current-question to re-sync attempt ID.
      ├── 401 (UNAUTHORIZED): Token expired or invalid. Prompt user to start fresh session.
      ├── 403 (FORBIDDEN): Session ownership mismatch. Display alert and switch profile option.
      ├── 404 (SESSION_NOT_FOUND): Session purged or missing. Remove from local index; route to Curriculum.
      ├── 409 (DUPLICATE_ATTEMPT): Attempt already submitted. Fetch current question to continue.
      ├── 410 (SESSION_EXPIRED): Session TTL reached. Display modal: "Session Expired. Start New Session".
      ├── 429 (RATE_LIMIT_EXCEEDED): Lock submit button; trigger countdown using Retry-After seconds.
      ├── 503/504 (PROVIDER TIMEOUT/UNAVAILABLE): Preserve user input; display non-blocking retry banner.
      └── 500 (INTERNAL_SERVER_ERROR): Display friendly toast: "Unexpected server error. Please retry."
```

---

## 7. Configuration & Environment Variables

All environment variables follow Vite conventions (`VITE_` prefix).

### `.env.example`
```ini
# Base URL for API requests.
# In local development with Vite dev server, leave empty or set to '/api/v1' to use proxy.
# In standalone production deployment, specify full URL: e.g. 'https://api.javadsa.example.com/api/v1'
VITE_API_BASE_URL=/api/v1

# App version displayed in footer
VITE_APP_VERSION=1.0.0
```

### Vite Development Proxy (`vite.config.ts`)
```typescript
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
```

---

## 8. Comprehensive Testing Strategy

The frontend testing pyramid mirrors the backend's strict verification standards:

### 1. Unit & Component Testing (Vitest + React Testing Library)
- **Scope:** Pure utility functions (token decoders, character counters, validators) and isolated UI components (`AnswerEditor`, `FeedbackBanner`, `ProgressBar`).
- **Target:** 100% logic coverage on error parsers, input restrictions, and retry counters.

### 2. Integration & Contract Testing (Mock Service Worker - MSW)
- **Scope:** Feature views (`SessionWorkspaceView`, `CurriculumView`) tested against mocked HTTP endpoints.
- **Contract Enforcement:** MSW handlers replicate the exact JSON responses and headers documented in `FRONTEND_API_CONTRACT.md`.
- **Scenarios Tested:**
  - Initial anonymous session start emitting `X-Learner-Token`.
  - Hint progressive levels (Level 1, Level 2).
  - Rate limiting 429 triggering countdown timer.
  - Session expiration 410 triggering redirection.
  - Idempotent answer resubmission returning cached PASS without duplicate state change.

### 3. End-to-End Testing (Playwright)
- **Scope:** Browser automated walkthrough against the real Spring Boot backend running in in-memory mode or PostgreSQL mode.
- **Test Flow:**
  1. Student opens Dashboard.
  2. Selects "Binary Search" from Curriculum.
  3. Session starts anonymously; token is stored.
  4. Answers Concept Assessment question; verifies PASS and stage transition to DERIVE.
  5. Requests a hint; verifies hint text is displayed.
  6. Refreshes browser; verifies session state rehydrates cleanly without data loss.

---

## 9. Phased Implementation Roadmap

To maintain bounding, verification, and code quality, implementation will be executed in 5 sequenced subphases during Phase 3B:

| Subphase | Focus | Deliverables |
| :--- | :--- | :--- |
| **Phase 3B.1** | Foundation & API Client | Vite scaffolding, Tailwind tokens, typed API client, token interceptors, mock fixtures. |
| **Phase 3B.2** | Curriculum & Dashboard | Global shell, Navbar, Home/Dashboard view, Curriculum directory, prerequisite badges. |
| **Phase 3B.3** | Learning Session Workspace | Session header, Question prompt, Answer editor (2000 char limit), Hint drawer, Feedback banner. |
| **Phase 3B.4** | Progress & Settings | Progress/Mastery view, Settings view, Token import/export, Session recovery by ID. |
| **Phase 3B.5** | Integration & E2E Validation | Vitest suite, MSW contract tests, Playwright E2E test, production build validation. |
