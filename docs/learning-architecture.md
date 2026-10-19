# Learning Architecture

## Overview
The Learning Architecture dictates how a user interacts with the system and how their interactions translate into measurable mastery.

## Unidirectional Data Flow
The flow of learning is strictly unidirectional and defined as follows:

```
User/Learner
    ↓
Learning Session
    ↓
Assessment -> Question Engine -> Hint Engine
    ↓
Evaluation Engine
    ↓
Evidence Engine
    ↓
Mastery Engine
    ↓
Learner Model
```

## Boundaries and Responsibilities
- **Learning Session:** The stateful controller orchestrating a single study block.
- **Question Engine:** Retrieves appropriate problems based on the Learner Model.
- **Evaluation Engine:** Tests submitted code or answers deterministically.
- **Evidence Engine:** Generates an immutable `EvidenceRecord` based on the evaluation.
- **Mastery Engine:** Updates the Learner Model based solely on `EvidenceRecord`s.

## Content Storage (Content-as-Data)
Pedagogical content is explicitly separated from Java application logic to prevent bloated "Content-as-Code" anti-patterns.
- **Storage Format:** Topic content is stored as schema-validated JSON files in `src/main/resources/topics/` (e.g., `binary-search.json`). This ensures content is version-controllable, deterministic, and easily modifiable by both humans and AI without touching core Java code.
- **Content Schema:** Files map directly to the `TopicTemplate` record, strictly organizing fields like `intuition`, `formalModel`, `derivation`, `correctness`, and `transferProblem`.
- **Loading & Validation:** The `TopicLoader` parses these JSON files into the domain models at runtime using Jackson. It provides strict validation (throwing `IllegalArgumentException` on missing required fields or invalid identifiers) with no silent fallbacks.
- **Relationship to Domain:** The loaded `TopicTemplate` acts purely as read-only context. The `AdaptiveQuestionEngine` and `AITutorEngine` consume this structured template to dynamically orchestrate the session.

## AI Security & Trust Boundaries
AI output is strictly treated as untrusted input. The LLM must NEVER be the authoritative source of learner state, mastery progression, or system mutation.
- **AI Response Contract:** AI evaluations must adhere to a strict structured JSON schema.
- **Validation Boundary:** The `AIResponseValidator` sanitizes raw string outputs. It explicitly strips unauthorized keywords (`action`, `set_mastery`, `mutate_state`) and guarantees a safe fallback (`INVALID_RESPONSE`) when the AI hallucinates, preventing prompt injection attacks from manipulating progression.
- **Authoritative Evidence Policy:** Even a perfectly validated AI `PASS` is not automatically authoritative evidence. The `EvidencePolicy` enforces context binding:
  - **Attempt Binding (Stale-Response Protection):** The AI assessment must exactly match the `attemptId` of the current `AttemptContext`. Replayed, stale, or duplicated responses are rejected.
  - **Context Binding:** The assessment must exactly match the expected `topicId` and `questionId`. A `PASS` for a Linear Search question cannot be used as evidence for a Binary Search question.
  - **Deterministic Implementation Evidence:** For `IMPLEMENTATION` and `DEBUGGING` states, an AI `PASS` is entirely ignored if the deterministic unit tests fail. AI provides diagnostics, but code execution provides the authoritative evaluation.
  - **Transfer Originality:** For `TRANSFER` states, evidence is bound explicitly to the transfer problem to prevent learners from bypassing it with their original answer.
- **Authoritative State:** `LessonFlow` acts solely on validated `EvidenceRecord`s passed through the `EvidencePolicy`. `MasteryEngine` computes its own deterministic values based on historical `EvidenceRecord`s.

## Dynamic State Machine (`LessonFlow`)
The rigid sequential lesson script has been replaced with a Deterministic Finite State Machine (FSM) implementing the **Golden Measure** of pedagogical completeness.

### The Golden Measure Pipeline
The learning pipeline enforces observable, rigorous skill acquisition before mastery is granted:
`UNDERSTAND → RECOGNIZE → DERIVE → IMPLEMENT → TEST → EXPLAIN → COUNTEREXAMPLE → OPTIMIZE → TRANSFER`

- **Concept Assessment:** Establishes core intuition and recognition signals.
- **Derivation (`DERIVE`):** The learner must actively deduce the algorithmic approach, key observations, or bounds before writing code. *Example: Recognizing that unsorted search is fundamentally bounded by O(N).*
- **Implementation (`IMPLEMENTATION`):** Deterministic execution. AI evaluates stylistic/logical intent, but unit tests provide authoritative correctness.
- **Explanation & Counterexample:** The learner must explain edge cases, invariants, and construct cases where a naive approach fails.
- **Optimization (`OPTIMIZE`):** The learner identifies bottlenecks, calculates time/space tradeoffs, and proves optimality bounds or improvements.
- **Transfer (`TRANSFER`):** Applies the generalized pattern to a novel problem context.

### Transitions and Safety
- **Dynamic Transitions:** Transitions are governed solely by the deterministic `EvaluationResult` (PASS, FAIL, PARTIAL, HINT) of an `EvidenceRecord`. Hints are distinctly tracked.
- **Remediation & Failure Loops:**
  - Failing an assessment (like Derivation or Optimization) routes the user into the `REMEDIATION` state, allowing adaptive recovery.
  - Failing implementation routes the user into the `DEBUGGING` state.
  - **Pedagogical Failure:** After 3 consecutive failures, the learner gracefully transitions to `SOLUTION_WALKTHROUGH` rather than abruptly terminating.
  - **Anti-Abuse Loop Protection:** A hard limit of 10 consecutive failures in a single state terminates the session (`FAILED_TERMINATED`) to prevent infinite automated bot loops.
