# Problem Model

## Overview
Defines the structure of a DSA problem.

## Core Properties
- **Problem ID:** Unique identifier.
- **Metadata:** Difficulty level, expected time complexity, expected space complexity.
- **Prerequisites:** Links to other `Topic`s that must be mastered first.
- **Tags:** Algorithmic patterns (e.g., "Sliding Window", "Two Pointers").
- **Test Cases:** Deterministic inputs and expected outputs for the Evaluation Engine.

## Question Types (The Golden Measure)
A problem isn't just a coding challenge; it is a matrix of pedagogical objectives mapped via `QuestionType`:
- **CONCEPT**: Core intuition.
- **DERIVATION**: Deriving the algorithm conceptually (bounds, search-space reduction).
- **IMPLEMENTATION**: Writing the actual code.
- **COMPLEXITY**: Understanding time/space bounds.
- **COUNTEREXAMPLE**: Identifying edge cases.
- **OPTIMIZATION**: Reasoning about bottlenecks, tradeoffs, and limits (e.g., lower bound limits).
- **TRANSFER**: Applying the pattern to novel contexts.

## Observable Evidence
Evidence must be strictly observable. We do not attempt to store hidden chain-of-thought.
- For `DERIVATION`: Assumptions, candidate approaches, rejected approaches, key observations, pseudocode.
- For `OPTIMIZATION`: Current complexity claims, bottleneck identification, tradeoff rationales, resulting complexity.

## Relationship to AI
The AI `QuestionGenerator` can generate new permutations of a `Problem` or prompt for derivation/optimization, but it must conform to this strict schema. All implementation code must be testable by the `EvaluationEngine` deterministically, and all AI evaluations of reasoning must pass through the `EvidencePolicy`.
