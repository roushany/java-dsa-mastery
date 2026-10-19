# Frontend User Experience (UX) Specification

**Project:** Java DSA Mastery
**Phase:** 3A (Frontend Architecture & API Contract)
**Target Platform:** Web (Desktop & Mobile, Responsive)
**Design Philosophy:** Minimalist, high-focus, accessible, zero-gamification pedagogy

---

## 1. Product Vision & Pedagogical Philosophy

Java DSA Mastery is designed for self-directed engineering students learning Data Structures and Algorithms in Java.

Unlike conventional platforms that rely on streak mechanics, badges, and superficial gamification, Java DSA Mastery focuses entirely on **deliberate practice and Socratic mastery**:
- **Zero-Friction Access:** No registration walls, account creation, or password management. Students begin learning in one click.
- **Cognitive Flow:** Clean, distraction-free typographic hierarchy with minimal visual clutter.
- **Evidence-Backed Progress:** Progression requires demonstrating understanding across conceptual derivation, implementation, edge-case analysis, and transfer learning.
- **Inclusive & Resilient:** Operates completely offline/locally without paid API keys (deterministic fallback evaluator), while supporting AI-assisted reasoning when configured.

---

## 2. Navigation Architecture & Global Shell

The application shell provides consistent orientation, status indicators, and keyboard landmarks across all viewports.

```
+---------------------------------------------------------------------------------------+
|  [Logo] Java DSA Mastery    |  Curriculum  |  Progress  |  Settings  |  (● AI Active) |
+---------------------------------------------------------------------------------------+
|                                                                                       |
|                                     ACTIVE VIEW                                       |
|                                                                                       |
+---------------------------------------------------------------------------------------+
|  Footer: Open-Source | Local Session: learner-a1b2c3d4 | API: Connected (v1.0.0)      |
+---------------------------------------------------------------------------------------+
```

### Global Shell Elements
1. **Header Navigation:**
   - Brand logo with link to Home (`/`).
   - Primary navigation links: `Curriculum`, `Progress`, `Settings / Help`.
   - Dynamic System Status Pill:
     - Green dot (`● AI Active`): Backend reports `aiConfigured: true`.
     - Gray dot (`○ Offline Evaluator`): Backend reports `aiConfigured: false` (in-memory deterministic mock active).
     - Red dot (`⚠ Server Disconnected`): Health check unreachable.
2. **Global Keyboard Shortcuts Modal (`?`):**
   - Accessible from any screen via `?` or footer link.
3. **Responsive Mobile Drawer:**
   - On screens `< 768px`, header collapses into a semantic navigation drawer with touch targets >= 48px.

---

## 3. Screen Specifications

### 3.1 Screen 1: Home / Dashboard (`/`)

The home view serves as the student's personal command center, immediately orienting them to where they left off.

```
+---------------------------------------------------------------------------------------+
|  WELCOME BACK                                                                         |
|                                                                                       |
|  +---------------------------------------------------------------------------------+  |
|  | RESUME ACTIVE SESSION                                                           |  |
|  | Topic: Binary Search                                                           |  |
|  | Current Stage: Concept Assessment (Stage 2 of 9) | Mastery: Novice              |  |
|  | [Continue Learning ->] (Enter)                                                  |  |
|  +---------------------------------------------------------------------------------+  |
|                                                                                       |
|  TOPIC PROGRESS OVERVIEW                                                              |
|  +----------------------+ +----------------------+ +----------------------+          |
|  | Linear Search        | | Binary Search        | | Two Pointers         |          |
|  | [Mastered ★★★]      | | [In Progress 2/9]    | | [Ready to Start]     |          |
|  | Prerequisites: Done  | | Prerequisites: Done  | | Prerequisites: Done  |          |
|  +----------------------+ +----------------------+ +----------------------+          |
|                                                                                       |
|  RECOMMENDED NEXT ACTION                                                              |
|  "Complete Binary Search to unlock Two Pointers and Binary Search on Answer."         |
+---------------------------------------------------------------------------------------+
```

#### Key Components & Interactions
- **Active Session Resume Card:**
  - Appears if a valid active session is cached in browser storage.
  - Displays topic name, current cognitive stage, mastery level, and a high-contrast primary CTA button (`Continue Learning`).
  - Pressing `Enter` automatically resumes this session.
- **Curriculum Snapshot Grid:**
  - Displays cards for core topics with badges: `Not Started`, `In Progress`, `Mastered`.
- **System Diagnostic Alert:**
  - If the backend returns `aiConfigured: false`, a friendly, dismissible info banner appears: *"Running with deterministic local evaluation. No Gemini key required."*

---

### 3.2 Screen 2: Curriculum Browser (`/curriculum`)

Provides a structured roadmap of DSA topics, clearly visualizing prerequisites and learning dependencies.

```
+---------------------------------------------------------------------------------------+
|  CURRICULUM ROADMAP                                                                   |
|                                                                                       |
|  Linear Search (Beginner) ──► Binary Search (Beginner) ──► Two Pointers (Intermediate)|
|                                     │                            │                    |
|                                     ▼                            ▼                    |
|                             Binary Search on Answer        Sliding Window             |
|                                                                  │                    |
|                                                                  ▼                    |
|                                                               Hashing                 |
|                                                                                       |
|  TOPIC DIRECTORY                                                                      |
|  +---------------------------------------------------------------------------------+  |
|  | Linear Search                                                  [Completed: 1/1] |  |
|  | Prerequisites: None | Difficulty: Beginner | Mastery: Mastered                  |  |
|  | [Review Topic]                                                                  |  |
|  +---------------------------------------------------------------------------------+  |
|  | Binary Search                                                  [In Progress: 2/9]| |
|  | Prerequisites: Linear Search | Difficulty: Beginner | Mastery: Novice           |  |
|  | [Continue Session]                                                              |  |
|  +---------------------------------------------------------------------------------+  |
|  | Two Pointers                                                    [Ready: 0/9]     |  |
|  | Prerequisites: Binary Search | Difficulty: Intermediate                          |  |
|  | [Start Session]                                                                 |  |
|  +---------------------------------------------------------------------------------+  |
+---------------------------------------------------------------------------------------+
```

#### Key Components & Interactions
- **Prerequisite Validation:**
  - Topics whose prerequisites have not been attempted display a subtle "Prerequisite recommended" badge.
  - Does NOT rigidly lock topics: students can click any topic to start, preserving adult learning autonomy.
- **Start / Resume CTA:**
  - If a session already exists for that topic, displays `Continue Session`.
  - Otherwise, displays `Start Topic`. Initiates `POST /api/v1/sessions` and routes to Learning Session view.

---

### 3.3 Screen 3: Learning Session Workspace (`/sessions/:sessionId`)

The focal learning environment where questions are answered, hints are explored, and feedback is received.

```
+---------------------------------------------------------------------------------------+
| [<- Curriculum]   Topic: Binary Search   Stage: Concept Assessment   Mastery: Novice   |
| [================================== 22% Completed =================================] |
+---------------------------------------------------------------------------------------+
|                                                           |                           |
|  PEDAGOGICAL PROMPT (Attempt: #1)                         |  HINTS & SOKRATIC HELP    |
|                                                           |                           |
|  Why is it impossible to eliminate half of an unsorted    |  [Request Hint 1]         |
|  array like we do in Binary Search?                       |                           |
|                                                           |  +---------------------+  |
|  Write your explanation below focusing on how ordering    |  | Hint 1 (Used):      |  |
|  allows interval elimination.                             |  | Order lets you      |  |
|                                                           |  | eliminate ranges.   |  |
|                                                           |  +---------------------+  |
|                                                           |                           |
|-----------------------------------------------------------+---------------------------|
|  YOUR ANSWER WORKSPACE (Markdown & Code supported)        |  EVALUATION FEEDBACK      |
|                                                           |                           |
|  +-----------------------------------------------------+  |  Status: Pending          |
|  | In an unsorted array, the target element could be   |  |                           |
|  | anywhere, so checking the middle element tells us   |  | Enter your answer and     |
|  | nothing about the remaining halves.                 |  | submit to receive         |
|  |                                                     |  | pedagogical feedback.     |
|  +-----------------------------------------------------+  |                           |
|  Chars: 134 / 2000                 [Submit Answer (Ctrl+Enter)]                       |
+---------------------------------------------------------------------------------------+
```

#### Detailed Layout Subcomponents

##### 1. Workspace Header
- **Breadcrumb:** Back link to `/curriculum`.
- **Session Metadata:** Topic title, current stage name formatted cleanly (`Concept Assessment`, `Derive`, `Implementation`, `Explanation`, `Debugging`, etc.).
- **Progress Bar:** Accessible progress bar showing nominal completion percentage.

##### 2. Question Prompt Area
- Formatted with accessible typography and high-contrast styling.
- Fenced code snippets formatted using monospace font with syntax highlighting.
- Clear distinction between conceptual prompts (text explanation) and code exercises (Java method implementation).

##### 3. Answer Input Area
- Accessible `<textarea>` or lightweight code input with:
  - Monospace font for code tasks, clean sans-serif for conceptual explanations.
  - Dynamic character count indicator: `"134 / 2000"`. Warns in yellow at > 1900 chars, prevents input at 2000 chars.
  - Placeholder guidance: *"Explain the core principle in your own words..."* or *"Write your Java method here..."*
  - Shortcut listener: `Ctrl + Enter` (or `Cmd + Enter` on macOS) triggers submission.

##### 4. Hint Drawer / Panel
- **Progressive Disclosure:** Clicking `Request Hint` calls `POST /api/v1/sessions/:id/request-hint`.
- Hints are presented sequentially in an accordion list.
- Transparent pedagogical notification: *"Hints provide guidance without penalizing completion, but are recorded in evidence history."*

##### 5. Evaluation Feedback Banner
Upon receiving `SubmitAnswerResponse`:
- **PASS:** Green banner. *"Correct! Stage advanced to Derive."* Accompanied by a prominent `Next Question` button.
- **FAIL:** Orange banner. Displays diagnostic feedback and detected misconception (e.g. `loop-syntax`, `index-out-of-bounds`). Explains that the session transitioned to `Remediation` or `Debugging` to reinforce the foundation.
- **RATE LIMIT (429):** Yellow countdown alert: *"Evaluation capacity reached. Please wait 5 seconds before submitting."* Submit button is temporarily disabled with live countdown.
- **PROVIDER ERROR (503/504):** Yellow banner: *"AI evaluation service timed out. Your answer has been preserved. [Retry Submission]"*.

##### 6. Terminal State Views
- **`COMPLETED`:** Topic celebration screen summarizing mastery level achieved, number of attempts, and button to unlock the next topic.
- **`FAILED_TERMINATED` (Loop protection after 10 attempts in one state):** Empathetic guidance screen explaining that the lesson paused to prevent frustration. Offers a button to review prerequisites or restart the topic with fresh attempts.

---

### 3.4 Screen 4: Progress & Mastery View (`/progress`)

Presents a clear overview of the student's mastery standings and evidence history.

```
+---------------------------------------------------------------------------------------+
|  LEARNER PROGRESS & MASTERY                                                           |
|  Learner ID: learner-a1b2c3d4                                                         |
|                                                                                       |
|  MASTERY BREAKDOWN                                                                    |
|  +---------------------------------------------------------------------------------+  |
|  | Linear Search                [Mastered ★★★★]             Last active: 2 hrs ago |  |
|  | Sessions: 1 | Stages Cleared: 1/1 | Attempts: 3 | Hints: 0                      |  |
|  +---------------------------------------------------------------------------------+  |
|  | Binary Search                [Novice ★☆☆☆]               Last active: Just now  |  |
|  | Sessions: 2 | Stages Cleared: 2/9 | Attempts: 5 | Hints: 2                      |  |
|  +---------------------------------------------------------------------------------+  |
|                                                                                       |
|  ACTIVE SESSIONS                                                                      |
|  - Binary Search: Stage CONCEPT_ASSESSMENT [Resume Session]                           |
|                                                                                       |
|  MASTERY CRITERIA EXPLANATION                                                         |
|  - Novice: Initial exploration, high assistance needed.                               |
|  - Familiar: Can recall algorithm principles and basic complexity.                    |
|  - Proficient: Solves derivations and implementations with minimal hints.             |
|  - Mastered: Successfully solves transfer problems and explains counterexamples.      |
+---------------------------------------------------------------------------------------+
```

---

### 3.5 Screen 5: Settings & Help (`/settings`)

Provides session recovery, identity migration, and operational transparency.

```
+---------------------------------------------------------------------------------------+
|  SETTINGS & SYSTEM HELP                                                               |
|                                                                                       |
|  ANONYMOUS LEARNER IDENTITY                                                           |
|  Current Identity: learner-a1b2c3d4                                                   |
|  Token: learner-a1b2c3d4.1729272000000.v4l1ds1gn4tur3... [Copy Token]                |
|  Use this token to continue learning on another browser or device.                    |
|                                                                                       |
|  IMPORT EXISTING TOKEN                                                                |
|  [ Paste Token Here                                          ] [Import Token]         |
|                                                                                       |
|  RECOVER SESSION BY ID                                                                |
|  [ 3fa85f64-5717-4562-b3fc-2c963f66afa6                      ] [Recover Session]      |
|                                                                                       |
|  SYSTEM ARCHITECTURE & PRIVACY                                                        |
|  - Zero Tracking: No analytics cookies, trackers, or third-party ads.                 |
|  - Privacy First: Anonymous UUID identity; no email or personal info stored.          |
|  - AI Independence: Functions locally via rule-based mock when Gemini is unconfigured.|
|  - Self-Hostable: Open-source backend running Spring Boot & PostgreSQL.               |
|                                                                                       |
|  [Reset Learner Identity (Start Fresh)]                                               |
+---------------------------------------------------------------------------------------+
```

---

## 4. Design System & Typography Tokens

The UI employs a high-contrast, clean developer-focused design token system.

### Color Palette
- **Backgrounds:**
  - Base: `#0F172A` (Slate 900)
  - Card / Panel: `#1E293B` (Slate 800)
  - Input / Workspace: `#0A0F1D` (Slate 950)
  - Border: `#334155` (Slate 700)
- **Text:**
  - Primary: `#F8FAFC` (Slate 50)
  - Secondary: `#94A3B8` (Slate 400)
  - Muted: `#64748B` (Slate 500)
- **Pedagogical Accents:**
  - Primary Action / Link: `#38BDF8` (Sky 400)
  - Success / Pass: `#22C55E` (Emerald 500)
  - Remediation / Fail: `#F97316` (Orange 500)
  - Warning / Rate Limit: `#FACC15` (Yellow 400)
  - Error: `#EF4444` (Red 500)

### Typography
- **Headings & UI Text:** Inter, system-ui, -apple-system, sans-serif
- **Code, Equations & Prompts:** JetBrains Mono, Fira Code, Menlo, monospace
- **Type Scale:**
  - H1: 2rem (32px), font-bold
  - H2: 1.5rem (24px), font-semibold
  - H3: 1.25rem (20px), font-medium
  - Body: 1rem (16px), line-height: 1.6
  - Caption / Meta: 0.875rem (14px)
  - Code: 0.9rem (14.5px), line-height: 1.5

---

## 5. Accessibility & Responsive Requirements

### Accessibility (WCAG 2.1 AA Compliance)
1. **Contrast Ratios:** All text elements exceed 4.5:1 against their backgrounds (minimum 7:1 for body copy).
2. **Focus Indicators:** Visible, high-contrast 2px focus ring (`outline: 2px solid #38BDF8; outline-offset: 2px`) on all interactive controls.
3. **Keyboard Navigation:**
   - Logical tab ordering across prompts, textareas, and buttons.
   - `Tab` moves between controls; `Enter` activates buttons; `Escape` closes modals and drawers.
   - `Ctrl+Enter` / `Cmd+Enter` submits answers.
4. **Screen Reader Live Regions:**
   - Feedback alerts use `role="status"` with `aria-live="polite"`.
   - Error messages use `role="alert"` with `aria-live="assertive"`.
   - Character counter uses `aria-describedby` linking textarea to the live counter.

### Responsive Breakpoints
- **Mobile (`< 640px`):** Single column. Question prompt stacked above answer workspace. Hint drawer rendered as bottom sheet.
- **Tablet (`640px - 1024px`):** Single column with collapsible side drawer.
- **Desktop (`> 1024px`):** Two-column split layout. Left: prompt and answer editor; Right: hints, feedback, and session progress.
