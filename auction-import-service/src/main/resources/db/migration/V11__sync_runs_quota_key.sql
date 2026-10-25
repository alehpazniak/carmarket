-- Which API key a persisted quota snapshot belongs to (first 12 hex chars of SHA-256 of the key, never the key),
-- so ApibaraQuotaTracker never reads another key's snapshot. Older rows have none and are ignored.
ALTER TABLE sync_runs
    ADD COLUMN IF NOT EXISTS quota_key_fingerprint VARCHAR(12);
