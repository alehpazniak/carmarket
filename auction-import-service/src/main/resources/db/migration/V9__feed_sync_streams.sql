-- Feed sync bookkeeping (docs/tasks/auction-feed-rollout.md, Stage B). Streams: "outcomes" (GET /changes) and one
-- per auction day, "open:2026-11-03" (GET /vehicles for that day). Rows of past days are deleted by the sync itself.
ALTER TABLE sync_streams
    ADD COLUMN IF NOT EXISTS cursor     TEXT,   -- next_cursor of an unfinished day pass
    ADD COLUMN IF NOT EXISTS last_total INT,    -- meta.total of the day at the start of its last pass
    ADD COLUMN IF NOT EXISTS pages_done INT,    -- pages read in the current / last pass
    ADD COLUMN IF NOT EXISTS sequence   BIGINT; -- outcomes: the last /changes sequence applied and committed
