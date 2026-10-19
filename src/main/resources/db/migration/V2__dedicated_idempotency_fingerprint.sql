-- =============================================================================
-- V2__dedicated_idempotency_fingerprint.sql
-- Java DSA Mastery: Add dedicated idempotency_fingerprint column on evidence_records
-- =============================================================================

ALTER TABLE evidence_records
    ADD COLUMN idempotency_fingerprint VARCHAR(64) NULL;

-- Backfill legacy records if any contain in-band comment trailers:
UPDATE evidence_records
SET idempotency_fingerprint = SUBSTRING(feedback FROM '<!-- idemp:([a-f0-9]{64}) -->'),
    feedback = REGEXP_REPLACE(feedback, '\s*<!-- idemp:[a-f0-9]{64} -->', '', 'g')
WHERE feedback LIKE '%<!-- idemp:%';
