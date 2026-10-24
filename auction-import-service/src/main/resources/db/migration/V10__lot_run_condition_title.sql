-- Run condition and title of a lot, as Apibara sends them (condition.run_condition.value, sale_document.name),
-- and per run how many records the feed sync dropped for their type, run condition or title (apibara.filters).
-- A new file rather than V9: V9 had already run on the development database.
ALTER TABLE auction_lots
    ADD COLUMN IF NOT EXISTS run_condition VARCHAR(50),
    ADD COLUMN IF NOT EXISTS title_type    VARCHAR(100);

ALTER TABLE sync_runs
    ADD COLUMN IF NOT EXISTS skipped_type      INT,
    ADD COLUMN IF NOT EXISTS skipped_condition INT,
    ADD COLUMN IF NOT EXISTS skipped_title     INT;
