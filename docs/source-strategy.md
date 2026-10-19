# Research and Source-Verification Workflow

Every technical topic added to this repository must undergo a rigorous research and source-verification process to ensure pedagogical accuracy and technical correctness.

## The Verification Workflow

For every technical topic, follow these steps:

1. **Identify Claims:** Isolate all conceptual, mathematical, and technical claims requiring verification.
2. **Find Primary Sources:** Always prefer primary sources over secondary tutorials, blogs, or AI assumptions.
3. **Java Behavior:** For anything related to the Java language or runtime, explicitly refer to the official Java documentation, Java Language Specification (JLS), or Java Virtual Machine Specification (JVMS).
4. **Algorithmic Correctness:** For DSA theory, use reputable academic textbooks or verifiable university lecture materials.
5. **Record Sources:** Register all important sources directly in the root `SOURCES.md` file.
6. **Distinguish Claim Types:** In documentation, code comments, and design discussions, explicitly categorize information as one of the following:
   - **Verified Fact:** A proven mathematical or technical truth.
   - **Interpretation:** An analysis or perspective on a proven fact.
   - **Pedagogical Simplification:** A deliberate omission of edge cases to aid early learning.
   - **Implementation Recommendation:** A community or industry standard best practice.
7. **Respect Copyright:** Do not copy copyrighted text unnecessarily. Synthesize concepts into original explanations while preserving licensing for external code or specific algorithms.
8. **Never Assume:** Do not present unverified claims as authoritative. If something is ambiguous, identify and document the ambiguity rather than inventing behavior.
