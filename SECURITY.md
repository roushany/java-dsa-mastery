# Security Policy

## 1. Supported Versions

We take security seriously and welcome vulnerability reports to keep learners, educators, and self-hosters safe.

| Version | Supported |
| :--- | :---: |
| 1.0.x (Current `master`) | :white_check_mark: |
| < 1.0.0 | :x: |

---

## 2. Reporting a Vulnerability

### Responsible Disclosure Process
Please **do not** open public GitHub issues or public pull requests for suspected security vulnerabilities. Publicly disclosing a flaw before a fix is available can put users and learners at risk.

### Preferred Reporting Method
1. **GitHub Private Vulnerability Reporting**:
   - Navigate to the **Security** tab of the GitHub repository.
   - Click on **Report a vulnerability** under the *Advisories* section.
   - This opens a private advisory draft directly with the maintainers where details can be shared and discussed confidentially.

2. **Alternative Private Contact**:
   - If GitHub Private Vulnerability Reporting is not enabled or available on your fork/clone, contact the project maintainers directly and privately through GitHub profile contact options before disclosing any sensitive details publicly.
   - Do not post exploit payloads, tokens, or replication steps in public discussion forums, issue trackers, or chat rooms.

---

## 3. What to Include in a Report

To help us investigate and resolve the issue quickly, please provide:
- A clear description of the vulnerability type (e.g. cross-learner access, rate-limiting bypass, injection).
- Step-by-step instructions or a minimal reproducible proof-of-concept.
- Affected components (e.g., `SessionSecurityInterceptor`, `RateLimitingInterceptor`, `tokenStorage.ts`).
- Potential impact and potential mitigations if known.

---

## 4. Response & Remediation Timeline

- **Initial Acknowledgment**: We strive to acknowledge receipt of security reports within **48–72 hours**.
- **Investigation & Assessment**: We will assess severity and verify the vulnerability in an isolated staging environment.
- **Coordinated Disclosure**: Once a fix is verified, a patched release and corresponding security advisory will be published.

---

## 5. Security Architecture Notes

- **Learner Capability Tokens**: Tokens use HMAC-SHA256 signatures verified exclusively on the Spring Boot backend via `LearnerTokenService`. Do not log or expose `JAVADSA_SECURITY_TOKEN_SECRET` in version control or browser bundles.
- **Rate Limiting**: In-memory token bucket rate limiting protects API endpoints against automated brute-force attacks.
- **Client Disclosures**: The frontend validates token expiry and structure for UX feedback, but backend authorization remains authoritative.
