# Product Architecture

## Overview
`java-dsa-mastery` is a modular, local-first Java application designed to be a world-class DSA learning and problem-solving repository.

## Core Principles
- **Separation of Concerns:** Strict division between deterministic application logic and probabilistic AI behavior.
- **Local First:** All state is persisted locally (e.g., flat files, in-memory) for the first milestone.

## Technology Stack
- **Language:** Java 25
- **Build System:** Maven
- **Testing:** JUnit 5

## Bounded Contexts
1. **Domain Context:** Core entities like `Topic`, `Curriculum`, `Problem`, `LearnerProfile`.
2. **Session Context:** Manages the lifecycle of a user interacting with problems.
3. **Engine Context:** The deterministic rule processors (`MasteryEngine`, `EvidenceEngine`, `EvaluationEngine`).
4. **AI Context:** Adapters for external LLMs (`Tutor`, `Evaluator`, `QuestionGenerator`).

## What is NOT Being Built Yet
- No databases (SQL/NoSQL) are used; local files suffice initially.
- No web frontend or REST APIs.
- No authentication or cloud infrastructure.
- No microservices; this is a strictly modular monolith.
