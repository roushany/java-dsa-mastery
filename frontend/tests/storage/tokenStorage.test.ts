import { describe, it, expect, beforeEach } from 'vitest';
import {
  TOKEN_STORAGE_KEY,
  TOKEN_MAX_AGE_MS,
  isValidLearnerToken,
  getStoredToken,
  storeToken,
  clearStoredToken,
  getLearnerIdFromToken,
} from '../../src/storage/tokenStorage';

describe('tokenStorage', () => {
  const validTimestamp = Date.now() - 10000; // 10 seconds ago
  const validToken = `learner-alice.${validTimestamp}.valid_signature_123`;

  beforeEach(() => {
    localStorage.clear();
  });

  describe('isValidLearnerToken', () => {
    it('accepts valid 3-part capability token', () => {
      expect(isValidLearnerToken(validToken)).toBe(true);
    });

    it('rejects null, undefined, and empty tokens', () => {
      expect(isValidLearnerToken(null)).toBe(false);
      expect(isValidLearnerToken(undefined)).toBe(false);
      expect(isValidLearnerToken('')).toBe(false);
      expect(isValidLearnerToken('   ')).toBe(false);
    });

    it('rejects tokens with wrong number of segments', () => {
      expect(isValidLearnerToken('only-one-part')).toBe(false);
      expect(isValidLearnerToken('two.parts')).toBe(false);
      expect(isValidLearnerToken('one.two.three.four')).toBe(false);
    });

    it('rejects invalid learner ID format', () => {
      expect(isValidLearnerToken(`invalid learner!.${validTimestamp}.sig`)).toBe(false);
      expect(isValidLearnerToken(`.${validTimestamp}.sig`)).toBe(false);
    });

    it('rejects non-numeric timestamps', () => {
      expect(isValidLearnerToken('learner-alice.notanumber.sig')).toBe(false);
      expect(isValidLearnerToken('learner-alice.-123.sig')).toBe(false);
    });

    it('rejects tokens older than 30 days', () => {
      const expiredTimestamp = Date.now() - (TOKEN_MAX_AGE_MS + 60000); // 30 days + 1 min
      expect(isValidLearnerToken(`learner-alice.${expiredTimestamp}.sig`)).toBe(false);
    });

    it('rejects tokens with timestamps more than 60s in the future', () => {
      const futureTimestamp = Date.now() + 120000; // 2 minutes in future
      expect(isValidLearnerToken(`learner-alice.${futureTimestamp}.sig`)).toBe(false);
    });

    it('rejects invalid signature characters (non-base64url)', () => {
      expect(isValidLearnerToken(`learner-alice.${validTimestamp}.sig with spaces!`)).toBe(false);
      expect(isValidLearnerToken(`learner-alice.${validTimestamp}.sig+with+plus=`)).toBe(false);
    });
  });

  describe('getStoredToken and storeToken', () => {
    it('returns null when no token is stored', () => {
      expect(getStoredToken()).toBeNull();
    });

    it('stores and retrieves a valid token', () => {
      const stored = storeToken(validToken);
      expect(stored).toBe(true);
      expect(getStoredToken()).toBe(validToken);
    });

    it('rejects storing an invalid token and leaves storage untouched', () => {
      storeToken(validToken);
      const stored = storeToken('invalid-token');
      expect(stored).toBe(false);
      expect(getStoredToken()).toBe(validToken);
    });

    it('clears corrupted storage and returns null', () => {
      localStorage.setItem(TOKEN_STORAGE_KEY, 'corrupted.garbage.data!!');
      expect(getStoredToken()).toBeNull();
      expect(localStorage.getItem(TOKEN_STORAGE_KEY)).toBeNull();
    });

    it('clears expired tokens from storage and returns null', () => {
      const expiredTimestamp = Date.now() - (TOKEN_MAX_AGE_MS + 5000);
      localStorage.setItem(TOKEN_STORAGE_KEY, `learner-bob.${expiredTimestamp}.sig`);
      expect(getStoredToken()).toBeNull();
      expect(localStorage.getItem(TOKEN_STORAGE_KEY)).toBeNull();
    });

    it('clears stored token explicitly', () => {
      storeToken(validToken);
      clearStoredToken();
      expect(getStoredToken()).toBeNull();
    });
  });

  describe('getLearnerIdFromToken', () => {
    it('extracts learner ID from valid token', () => {
      expect(getLearnerIdFromToken(validToken)).toBe('learner-alice');
    });

    it('returns null for invalid or null tokens', () => {
      expect(getLearnerIdFromToken(null)).toBeNull();
      expect(getLearnerIdFromToken('garbage')).toBeNull();
    });
  });
});
