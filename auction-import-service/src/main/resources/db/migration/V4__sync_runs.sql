-- One row per parser run of LotSyncScheduler (scheduled or started from the admin panel)
CREATE TABLE IF NOT EXISTS sync_runs
(
    id           UUID PRIMARY KEY,
    parser       VARCHAR(100) NOT NULL,
    trigger      VARCHAR(20)  NOT NULL,
    started_at   TIMESTAMPTZ  NOT NULL,
    finished_at  TIMESTAMPTZ,
    lots_fetched INT,
    lots_added   INT,
    lots_updated INT,
    error        VARCHAR(1000)
);

CREATE INDEX IF NOT EXISTS idx_sync_runs_parser_started ON sync_runs (parser, started_at DESC);
