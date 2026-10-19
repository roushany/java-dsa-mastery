/**
 * Session Index Storage Abstraction
 *
 * Security & Integrity Guarantees:
 * - Independent Storage: Completely decoupled from token storage key.
 * - No Progress Fabrication: Does not store answers, question contents, or mock evaluations.
 *   Only holds lightweight pointers (UUID, topicId, timestamp) to facilitate "Resume Session".
 * - Resilient Parsing: Recovers gracefully from corrupted, non-array, or invalid JSON payloads.
 */

export const SESSION_INDEX_STORAGE_KEY = 'javadsa_session_index';

const UUID_REGEX = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;
const TOPIC_ID_REGEX = /^[a-z0-9-]+$/;

export interface SessionIndexEntry {
  sessionId: string;
  topicId: string;
  createdAt: number;
  updatedAt: number;
  lastKnownState?: string;
  lastKnownMastery?: string;
}

/**
 * Validates a single session entry object.
 */
export function isValidSessionEntry(item: unknown): item is SessionIndexEntry {
  if (!item || typeof item !== 'object') {
    return false;
  }

  const candidate = item as Partial<SessionIndexEntry>;

  if (typeof candidate.sessionId !== 'string' || !UUID_REGEX.test(candidate.sessionId)) {
    return false;
  }

  if (typeof candidate.topicId !== 'string' || !TOPIC_ID_REGEX.test(candidate.topicId)) {
    return false;
  }

  if (typeof candidate.createdAt !== 'number' || !Number.isFinite(candidate.createdAt)) {
    return false;
  }

  if (typeof candidate.updatedAt !== 'number' || !Number.isFinite(candidate.updatedAt)) {
    return false;
  }

  if (candidate.lastKnownState !== undefined && typeof candidate.lastKnownState !== 'string') {
    return false;
  }

  if (candidate.lastKnownMastery !== undefined && typeof candidate.lastKnownMastery !== 'string') {
    return false;
  }

  return true;
}

/**
 * Retrieves all valid sessions from localStorage.
 * Automatically purges malformed or non-array data.
 */
export function getStoredSessions(): SessionIndexEntry[] {
  try {
    const raw = localStorage.getItem(SESSION_INDEX_STORAGE_KEY);
    if (!raw) {
      return [];
    }

    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) {
      localStorage.removeItem(SESSION_INDEX_STORAGE_KEY);
      return [];
    }

    // Filter to only schema-compliant entries
    const validEntries = parsed.filter(isValidSessionEntry);

    // If corrupted entries were found and pruned, rewrite cleaned array
    if (validEntries.length !== parsed.length) {
      localStorage.setItem(SESSION_INDEX_STORAGE_KEY, JSON.stringify(validEntries));
    }

    return validEntries;
  } catch {
    // Malformed JSON or storage failure
    try {
      localStorage.removeItem(SESSION_INDEX_STORAGE_KEY);
    } catch {
      // Graceful no-op
    }
    return [];
  }
}

/**
 * Inserts or updates a session entry in localStorage.
 */
export function saveSession(entry: SessionIndexEntry): boolean {
  if (!isValidSessionEntry(entry)) {
    return false;
  }

  try {
    const current = getStoredSessions();
    const existingIndex = current.findIndex((s) => s.sessionId === entry.sessionId);

    if (existingIndex >= 0) {
      current[existingIndex] = entry;
    } else {
      current.unshift(entry); // Newest first
    }

    localStorage.setItem(SESSION_INDEX_STORAGE_KEY, JSON.stringify(current));
    return true;
  } catch {
    return false;
  }
}

/**
 * Removes a session by ID from the index.
 */
export function removeSession(sessionId: string): boolean {
  try {
    const current = getStoredSessions();
    const filtered = current.filter((s) => s.sessionId !== sessionId);

    if (filtered.length !== current.length) {
      localStorage.setItem(SESSION_INDEX_STORAGE_KEY, JSON.stringify(filtered));
      return true;
    }
    return false;
  } catch {
    return false;
  }
}

/**
 * Clears the session index completely.
 */
export function clearSessionIndex(): void {
  try {
    localStorage.removeItem(SESSION_INDEX_STORAGE_KEY);
  } catch {
    // Graceful no-op
  }
}
