/**
 * Learner Token Storage Abstraction
 *
 * Security Trade-offs & Architecture Notes:
 * - Scope: The token is a capability identifier (<learnerId>.<timestampMs>.<signature>)
 *   signed with HMAC-SHA256 by the Spring Boot backend. It contains NO personal identifiable
 *   information (PII), passwords, or secrets.
 * - Storage Mechanism: Stored in localStorage for persistent student session continuity.
 * - Threat Model: If localStorage is compromised via an XSS vulnerability, the attacker
 *   gains temporary access only to this anonymous learner's practice history. They cannot
 *   escalate privileges, access cloud infrastructure, or read server secrets.
 * - Expiration: Enforces client-side validation against the backend's 30-day validity window.
 */

export const TOKEN_STORAGE_KEY = 'javadsa_learner_token';

// 30 days in milliseconds matching LearnerTokenService.validityDays
export const TOKEN_MAX_AGE_MS = 30 * 24 * 60 * 60 * 1000;
// 60 seconds tolerance for clock skew
export const CLOCK_SKEW_TOLERANCE_MS = 60 * 1000;

const LEARNER_ID_REGEX = /^[a-zA-Z0-9_-]{1,64}$/;
const BASE64URL_REGEX = /^[A-Za-z0-9_-]+$/;

/**
 * Validates token structure and freshness matching LearnerTokenService.validateToken
 */
export function isValidLearnerToken(token: unknown): token is string {
  if (typeof token !== 'string' || token.trim() === '') {
    return false;
  }

  const parts = token.trim().split('.');
  if (parts.length !== 3) {
    return false;
  }

  const [learnerId, timestampStr, signature] = parts;

  // 1. Learner ID validation
  if (!LEARNER_ID_REGEX.test(learnerId)) {
    return false;
  }

  // 2. Timestamp validation
  const timestamp = Number(timestampStr);
  if (!Number.isInteger(timestamp) || timestamp <= 0) {
    return false;
  }

  const now = Date.now();
  // Tokens older than 30 days or more than 60s in the future are rejected
  if (now - timestamp > TOKEN_MAX_AGE_MS || timestamp > now + CLOCK_SKEW_TOLERANCE_MS) {
    return false;
  }

  // 3. Signature format validation (Base64URL)
  if (!BASE64URL_REGEX.test(signature)) {
    return false;
  }

  return true;
}

/**
 * Retrieves the currently stored learner token, verifying its validity.
 * Automatically clears corrupted or expired tokens.
 */
export function getStoredToken(): string | null {
  try {
    const raw = localStorage.getItem(TOKEN_STORAGE_KEY);
    if (!raw) {
      return null;
    }

    if (isValidLearnerToken(raw)) {
      return raw.trim();
    }

    // Token is corrupt or expired; clear it safely
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    return null;
  } catch {
    // Storage access restricted (e.g. private browsing mode)
    return null;
  }
}

/**
 * Saves a valid learner token to localStorage.
 * Rejects invalid tokens without altering storage.
 */
export function storeToken(token: string): boolean {
  if (!isValidLearnerToken(token)) {
    return false;
  }

  try {
    localStorage.setItem(TOKEN_STORAGE_KEY, token.trim());
    return true;
  } catch {
    return false;
  }
}

/**
 * Clears the stored learner token.
 */
export function clearStoredToken(): void {
  try {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
  } catch {
    // Graceful no-op
  }
}

/**
 * Extracts learnerId from the valid token without validating cryptographic signature
 * (signature is validated exclusively on the server).
 */
export function getLearnerIdFromToken(token: string | null): string | null {
  if (!token || !isValidLearnerToken(token)) {
    return null;
  }
  return token.split('.')[0];
}
