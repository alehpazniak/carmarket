-- Apibara feed sync (docs/tasks/auction-feed-sync.md): vehicle type, the outcome of ended lots, feed bookkeeping

-- vehicle_type / body_style as Apibara sends them (Vehicle.type, vehicle_specs.body_style); sold_price and
-- ended_at only for ended lots; last_seen_at = the last sync that returned the lot, changed or not
ALTER TABLE auction_lots
    ADD COLUMN IF NOT EXISTS vehicle_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS body_style   VARCHAR(50),
    ADD COLUMN IF NOT EXISTS sold_price   NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS ended_at     TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_auction_lots_vehicle_type ON auction_lots (vehicle_type);

-- One row per sync stream (e.g. copart:open), filled by the feed sync
CREATE TABLE IF NOT EXISTS sync_streams
(
    stream_key       VARCHAR(100) PRIMARY KEY,
    watermark        TIMESTAMPTZ,
    last_full_at     TIMESTAMPTZ,
    last_run_at      TIMESTAMPTZ,
    backlog          BOOLEAN NOT NULL DEFAULT FALSE,
    backlog_estimate INT
);

ALTER TABLE sync_runs
    ADD COLUMN IF NOT EXISTS mode                 VARCHAR(20),
    ADD COLUMN IF NOT EXISTS stop_reason          VARCHAR(30),
    ADD COLUMN IF NOT EXISTS window_minutes       INT,
    ADD COLUMN IF NOT EXISTS streams_done         INT,
    ADD COLUMN IF NOT EXISTS streams_with_backlog INT;
