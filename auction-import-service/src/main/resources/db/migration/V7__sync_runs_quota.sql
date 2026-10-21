-- What a sync run spent and the Apibara quota headers it saw last (ApibaraQuotaTracker),
-- so the admin status and the budget survive a restart
ALTER TABLE sync_runs
    ADD COLUMN IF NOT EXISTS api_requests       INT,
    ADD COLUMN IF NOT EXISTS quota_limit        INT,
    ADD COLUMN IF NOT EXISTS quota_used         INT,
    ADD COLUMN IF NOT EXISTS quota_remaining    INT,
    ADD COLUMN IF NOT EXISTS quota_period_start TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS quota_period_end   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS quota_observed_at  TIMESTAMPTZ;
