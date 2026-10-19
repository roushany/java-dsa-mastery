# Java DSA Mastery

An open-source, student-focused learning platform designed for mastering Data Structures & Algorithms in Java through structured pedagogical progressions, interactive practice questions, progressive hints, and evidence-backed topic mastery feedback.

Built with a **Java 21 Spring Boot REST API** and a **React 18 + TypeScript + Vite frontend**.

---

## 1. System Architecture & Prerequisites

### Architecture Overview
```
┌─────────────────────────────────┐        HTTP / REST        ┌──────────────────────────────────┐
│   React 18 + TypeScript Client  │ ────────────────────────> │      Spring Boot REST Backend    │
│       (Vite Dev Server: 5173)   │ <──────────────────────── │       (Embedded Tomcat: 8080)    │
│  - Roadmap & Learning Workspace │    X-Learner-Token HMAC   │  - State Machine & Lesson Flow   │
│  - Browser LocalStorage Index   │    /api/v1/* Endpoints    │  - Deterministic Mock Evaluator  │
└─────────────────────────────────┘                           └──────────────────────────────────┘
                                                                               │
                                                                 (Optional Persistence Profile)
                                                                               ▼
                                                              ┌──────────────────────────────────┐
                                                              │      PostgreSQL 17/18 Database   │
                                                              │       (Default Dev Port: 5433)   │
                                                              │  - Flyway Versioned Migrations   │
                                                              │  - Atomic Attempt Transitions    │
                                                              └──────────────────────────────────┘
```

### Prerequisites & Supported Versions
- **Java Development Kit (JDK):** Java 21 LTS (Oracle JDK or Eclipse Temurin recommended; Java 21–25 supported, byte-code target release is 21).
- **Node.js & npm:** Node.js 20 LTS or 22 LTS, npm 10+ (tested with Node v22.19.0).
- **Build Tool:** Maven 3.9+ (bundled via Maven Wrapper `mvnw` / `mvnw.cmd`).
- **Database (Optional):** PostgreSQL 17 or 18 on port `5433` (the backend runs in zero-dependency in-memory mode by default).
- **Browser:** Modern Chromium (Edge 120+, Chrome 120+), Firefox, or Safari.

---

## 2. Quickstart: Full Local Development

Running the platform locally requires two terminal sessions: one for the Spring Boot backend and one for the React frontend.

### Terminal 1: Backend Service (Port 8080)

```powershell
# Windows PowerShell
.\mvnw.cmd clean test       # Run 248 backend tests (240 pass, 8 opt-in/Docker tests safely skipped)
.\mvnw.cmd spring-boot:run   # Starts backend on http://localhost:8080
```

```bash
# Linux / macOS / Bash
./mvnw clean test
./mvnw spring-boot:run
```

Verify backend health:
```bash
curl http://localhost:8080/api/v1/health
# Response: {"status":"UP","version":"1.0.0","activeTopics":7,"aiConfigured":false}
```

### Terminal 2: Frontend Application (Port 5173)

```bash
cd frontend

# Install dependencies strictly from pinned lockfile
npm ci

# Run test suite, typecheck, and build
npm test             # Run 14 test suites (142 tests passing)
npm run typecheck    # Validate TypeScript with 0 errors
npm run build        # Build production bundle in dist/

# Start Vite development server
npm run dev
```

Open your browser at **`http://localhost:5173`**. The Vite development server automatically proxies all `/api` requests to `http://localhost:8080`.

---

## 3. Configuration & Environment Variables

Environment templates with safe defaults are provided at `.env.example` (repository root) and `frontend/.env.example`.

### Backend & Database Variables (`.env`)

| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | `default` | `default` (in-memory mode) or `postgres` (persistent mode). |
| `JAVADSA_PERSISTENCE_ENABLED` | `false` | Enables PostgreSQL database repositories and transactional commits. |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://127.0.0.1:5433/java_dsa_mastery_phase2b_test` | JDBC connection URL. For cloud providers (Neon/Supabase), append `?sslmode=require`. |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | Database username. |
| `SPRING_DATASOURCE_PASSWORD` | *(empty)* | Database password. |
| `JAVADSA_SECURITY_TOKEN_SECRET` | *(built-in 32-char default)* | HMAC-SHA256 secret key for signing learner capability tokens (`X-Learner-Token`). Must be 32+ characters in production. |
| `JAVADSA_CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` | Comma-separated CORS allowed origins. |
| `GEMINI_API_KEY` | *(empty)* | Optional Google AI Studio Gemini API key. If omitted, the engine uses the deterministic offline mock evaluator ($0 budget). |
| `JAVADSA_RATE_LIMIT_ENABLED` | `true` | Enforces multi-tier rate limiting (300 general RPM, 15 learner AI RPM, 10 session AI RPM). |
| `JAVADSA_SESSION_TTL_HOURS` | `24` | Session time-to-live. Expired sessions return HTTP 410 Gone (`SESSION_EXPIRED`). |

### Frontend Variables (`frontend/.env`)

| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `VITE_API_BASE_URL` | `/api/v1` | Base URL for API requests. Defaults to `/api/v1` (proxied by Vite to `http://localhost:8080/api/v1` in dev mode). For hosted production deployments where frontend and backend are on different domains, set to the absolute backend URL (e.g. `https://api.yourdomain.com/api/v1`). |

---

## 4. PostgreSQL Persistent Mode

In persistent mode, sessions, attempts, evidence records, and topic mastery snapshots are stored in PostgreSQL with strict transactional guarantees and Flyway migrations.

```powershell
# Create database on port 5433
createdb -p 5433 -h 127.0.0.1 -U postgres java_dsa_mastery_phase2b_test

# Run full test suite with PostgreSQL active (includes TransactionalPostgresSessionIntegrationTest)
.\mvnw.cmd test '-Dspring.profiles.active=postgres'

# Run application with PostgreSQL persistence
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=postgres'
```

---

## 5. Continuous Integration (GitHub Actions)

The repository workflow ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) automatically runs on pushes and pull requests to `main` and `master`:
1. **Java Backend Job (`build`)**: Sets up Java 21 LTS, provisions a live `postgres:17-alpine` container service, compiles code, runs all in-memory unit tests, runs all 17 transactional PostgreSQL scenarios, and packages the executable JAR.
2. **React Frontend Job (`frontend`)**: Sets up Node.js 20 LTS, validates `package-lock.json` presence, runs `npm ci`, executes all 142 Vitest unit and integration tests, verifies TypeScript with `tsc --noEmit`, and builds the production bundle with `vite build`.

---

## 6. Current Platform Characteristics & Limitations

To ensure pedagogical honesty and transparent development, the following characteristics should be noted:

1. **No Sandboxed Java Code Execution**: Submitted Java code is analyzed for pedagogical structure; it is **not compiled or executed** inside a secure container sandbox, and automated unit-test execution is currently unavailable. Implementation-stage submissions transition to the debugging stage for guided inspection rather than receiving an automated compiler pass.
2. **Browser-Local Session Indexing**: Anonymous learner capability tokens (`X-Learner-Token`) and session pointers are stored on the student's browser via `localStorage`. The backend maintains strict HMAC cryptographic ownership, but does not provide a central cross-device account history endpoint. Students can export and import their token from the **Settings** view to continue learning on another device.
3. **Deterministic Offline AI Fallback**: The core learning experience works completely without a Gemini API key (₹0/month budget target). When `GEMINI_API_KEY` is not configured, the platform operates deterministically using built-in mock evaluators and rubric matchers.
4. **Docker Verification Status**: Container definitions (`Dockerfile`, `docker-compose.yml`) and CI service container configurations are included in the repository, but local host-level Docker execution has been recorded as **UNVERIFIED** due to the absence of a local Docker daemon on the test environment.
