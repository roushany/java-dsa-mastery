# AI Architecture

## Overview
The AI layer is an isolated subsystem responsible for generating text, hints, and conceptual analysis. It is **probabilistic** and must never directly mutate the Learner Model.

## The Crucial Principle
**AI recommends, evaluates, and generates. Deterministic application logic decides state.**

## AI Components
1. **Tutor:** Generates pedagogical hints and explanations based on user struggles.
2. **Evaluator:** Analyzes conceptual answers or code quality. It outputs structured data (e.g., mapped to a strict Java Enum like `CORRECT` or `PARTIAL`) rather than arbitrary string grades.
3. **Question Generator:** Creates novel problem variations based on base templates.

## Data Flow
- **Input:** The `Session` provides sanitized context (problem description, user attempt).
- **Output:** The LLM Provider returns raw text.
- **Translation:** The AI adapters parse the raw text into deterministic DTOs.
- **Consumption:** The Engine Context consumes the DTOs and makes the final state decisions.

## Dependencies
- Interfaces for `Tutor`, `Evaluator`, and `QuestionGenerator` are defined in the domain/core.
- Concrete LLM implementations (e.g., Gemini API) reside purely in infrastructure modules.
