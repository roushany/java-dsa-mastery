import { describe, it, expect, beforeEach } from 'vitest';
import {
  SESSION_INDEX_STORAGE_KEY,
  SessionIndexEntry,
  isValidSessionEntry,
  getStoredSessions,
  saveSession,
  removeSession,
  clearSessionIndex,
} from '../../src/storage/sessionIndexStorage';
import {
  TOKEN_STORAGE_KEY,
  storeToken,
  getStoredToken,
} from '../../src/storage/tokenStorage';

describe('sessionIndexStorage', () => {
  const sampleUuid = '12345678-1234-1234-1234-123456789abc';
  const validEntry: SessionIndexEntry = {
    sessionId: sampleUuid,
    topicId: 'binary-search',
    createdAt: 1729272000000,
    updatedAt: 1729272100000,
    lastKnownState: 'CONCEPT_ASSESSMENT',
    lastKnownMastery: 'NOVICE',
  };

  beforeEach(() => {
    localStorage.clear();
  });

  describe('isValidSessionEntry', () => {
    it('accepts valid session index entry', () => {
      expect(isValidSessionEntry(validEntry)).toBe(true);
    });

    it('rejects null, non-objects, and missing required fields', () => {
      expect(isValidSessionEntry(null)).toBe(false);
      expect(isValidSessionEntry('string')).toBe(false);
      expect(isValidSessionEntry({})).toBe(false);
      expect(isValidSessionEntry({ sessionId: sampleUuid })).toBe(false);
    });

    it('rejects invalid UUID formats', () => {
      expect(isValidSessionEntry({ ...validEntry, sessionId: 'not-a-uuid' })).toBe(false);
      expect(isValidSessionEntry({ ...validEntry, sessionId: '123-456' })).toBe(false);
    });

    it('rejects invalid topicId formats', () => {
      expect(isValidSessionEntry({ ...validEntry, topicId: 'Invalid Topic!' })).toBe(false);
      expect(isValidSessionEntry({ ...validEntry, topicId: '' })).toBe(false);
    });

    it('rejects non-numeric timestamps', () => {
      expect(isValidSessionEntry({ ...validEntry, createdAt: NaN })).toBe(false);
      expect(isValidSessionEntry({ ...validEntry, updatedAt: Infinity })).toBe(false);
    });
  });

  describe('CRUD operations & corrupted storage handling', () => {
    it('returns empty array when nothing is stored', () => {
      expect(getStoredSessions()).toEqual([]);
    });

    it('saves a new session entry', () => {
      const saved = saveSession(validEntry);
      expect(saved).toBe(true);
      const sessions = getStoredSessions();
      expect(sessions).toHaveLength(1);
      expect(sessions[0]).toEqual(validEntry);
    });

    it('updates existing session entry by sessionId', () => {
      saveSession(validEntry);
      const updatedEntry: SessionIndexEntry = {
        ...validEntry,
        lastKnownState: 'DERIVE',
        updatedAt: 1729272500000,
      };
      saveSession(updatedEntry);

      const sessions = getStoredSessions();
      expect(sessions).toHaveLength(1);
      expect(sessions[0].lastKnownState).toBe('DERIVE');
      expect(sessions[0].updatedAt).toBe(1729272500000);
    });

    it('removes an existing session by ID', () => {
      saveSession(validEntry);
      const removed = removeSession(sampleUuid);
      expect(removed).toBe(true);
      expect(getStoredSessions()).toHaveLength(0);
    });

    it('clears all sessions via clearSessionIndex', () => {
      saveSession(validEntry);
      clearSessionIndex();
      expect(getStoredSessions()).toEqual([]);
    });

    it('handles corrupted JSON by resetting storage to empty array', () => {
      localStorage.setItem(SESSION_INDEX_STORAGE_KEY, '{invalid: json');
      expect(getStoredSessions()).toEqual([]);
      expect(localStorage.getItem(SESSION_INDEX_STORAGE_KEY)).toBeNull();
    });

    it('handles non-array stored data by resetting storage', () => {
      localStorage.setItem(SESSION_INDEX_STORAGE_KEY, JSON.stringify({ not: 'an array' }));
      expect(getStoredSessions()).toEqual([]);
      expect(localStorage.getItem(SESSION_INDEX_STORAGE_KEY)).toBeNull();
    });

    it('filters out corrupted items from array while preserving valid items', () => {
      const mixed = [validEntry, { invalid: 'corrupt item' }, 'pure string'];
      localStorage.setItem(SESSION_INDEX_STORAGE_KEY, JSON.stringify(mixed));

      const sessions = getStoredSessions();
      expect(sessions).toHaveLength(1);
      expect(sessions[0]).toEqual(validEntry);
    });
  });

  describe('Token and Session Index Separation', () => {
    it('maintains strict logical separation between token storage and session index', () => {
      const validToken = `learner-charlie.${Date.now() - 1000}.valid_signature`;

      // Store both
      storeToken(validToken);
      saveSession(validEntry);

      // Verify distinct keys in localStorage
      expect(localStorage.getItem(TOKEN_STORAGE_KEY)).toBe(validToken);
      expect(JSON.parse(localStorage.getItem(SESSION_INDEX_STORAGE_KEY)!)).toHaveLength(1);

      // Mutating session index does not affect token
      clearSessionIndex();
      expect(getStoredSessions()).toEqual([]);
      expect(getStoredToken()).toBe(validToken);

      // Corrupting session index does not corrupt token
      localStorage.setItem(SESSION_INDEX_STORAGE_KEY, 'corrupt-session-data');
      expect(getStoredSessions()).toEqual([]);
      expect(getStoredToken()).toBe(validToken);
    });
  });
});
