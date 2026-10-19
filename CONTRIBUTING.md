# Contributing to Java DSA Mastery

Thank you for your interest in contributing to **Java DSA Mastery**, an open-source, student-focused platform designed to help learners master Data Structures & Algorithms through structured pedagogical progressions.

---

## 1. Code of Conduct & Core Values

We are dedicated to providing a welcoming, constructive, and respectful environment for all contributors.
- **Pedagogical Honesty**: Clearly document system limitations. Never fabricate evidence or claim capabilities that the backend does not actually support (e.g. claiming code sandbox execution when code is not executed).
- **Budget Integrity (₹0 Target)**: The core learning experience must run completely without paid cloud services, mandatory third-party subscriptions, or required external API keys.
- **Security & Privacy**: Protect learner capability tokens. Never commit API keys, credentials, or private tokens to Git history.

---

## 2. Getting Started & Development Setup

### Prerequisites
- **Java:** JDK 21 LTS or newer (Oracle JDK or Eclipse Temurin).
- **Node.js:** Node.js 20 LTS or 22 LTS, npm 10+.
- **Maven:** Bundled via `./mvnw` (Linux/macOS) and `.\mvnw.cmd` (Windows).

### Fork & Clone
```bash
git clone https://github.com/<your-username>/java-dsa-mastery.git
cd java-dsa-mastery
```

### Backend Setup
```bash
# Verify test suite
./mvnw clean test

# Run backend service (port 8080)
./mvnw spring-boot:run
```

### Frontend Setup
```bash
cd frontend

# Install exact pinned dependencies
npm ci

# Run test suite and typecheck
npm test
npm run typecheck
npm run build

# Start Vite dev server (port 5173)
npm run dev
```

---

## 3. Branching & Pull Request Process

1. **Branch Naming**:
   - `feat/<topic-or-feature-name>` for new features or curriculum expansions.
   - `fix/<bug-or-issue-description>` for bug fixes and corrections.
   - `docs/<documentation-topic>` for documentation improvements.
   - `refactor/<subsystem>` for code refactoring without feature changes.

2. **Commit Hygiene**:
   - Write clear, concise commit messages explaining *why* a change was made.
   - Keep pull requests focused on a single concern or phase.
   - Ensure no `.env` files, build artifacts (`target/`, `frontend/dist/`), or test log dumps are committed.

3. **Pull Request Checklist**:
   Before opening a PR, ensure all tests pass locally:
   - [ ] Backend tests pass: `./mvnw test`
   - [ ] Frontend tests pass: `npm test` (in `frontend/`)
   - [ ] TypeScript typecheck passes: `npm run typecheck` (in `frontend/`)
   - [ ] Production build succeeds: `npm run build` (in `frontend/`)
   - [ ] No regression of the ₹0 budget goal (mock evaluator continues to work offline).
   - [ ] Any new configuration variables are documented in `.env.example`.

---

## 4. Testing & Architectural Guidelines

- **Backend**:
  - Ground domain models in the established state machine and lesson flow.
  - Preserve the 17 transactional PostgreSQL test scenarios in `TransactionalPostgresSessionIntegrationTest`.
  - Maintain `@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")` for live AI tests so they remain optional and free.
- **Frontend**:
  - Follow the existing React 18 + TypeScript architecture.
  - Use typed API models defined in `frontend/src/api/types.ts`.
  - Maintain keyboard accessibility (`aria-label`, focus states) and clear error/empty states.
  - Do not install heavy external libraries if standard browser or React capabilities suffice.

---

## 5. Reporting Issues & Feedback

- Use **GitHub Issues** to report bugs or suggest pedagogical improvements.
- When reporting a bug, please include:
  - Operating system and Java/Node versions.
  - Steps to reproduce.
  - Relevant logs or screenshots (with any capability tokens or personal data redacted).
