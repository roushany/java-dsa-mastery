# Testing Strategy

## Overview
The project enforces a strict testing strategy to ensure the deterministic learning logic is flawless.

## Frameworks
- **JUnit 5:** Primary testing framework.
- **Mockito/AssertJ:** For mocking interfaces and fluent assertions.

## Rules
- **Every meaningful code change requires tests.**
- **Domain & Engine Testing:** The deterministic engines (`MasteryEngine`, `EvidenceEngine`) must have 100% test coverage.
- **AI Isolation:** The AI components must be aggressively mocked during core test runs. A unit test must never fail due to an LLM hallucination or network timeout.
- **Integration Tests:** Used sparingly in early milestones to verify that adapters correctly map static LLM JSON responses to deterministic DTOs.
