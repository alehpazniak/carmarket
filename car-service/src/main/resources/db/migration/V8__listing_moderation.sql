-- Moderation: BLOCKED listings (hidden publicly, visible to the owner with the reason) and user reports.
DO $$
BEGIN
    IF to_regclass('car_listings') IS NOT NULL THEN
        ALTER TABLE car_listings DROP CONSTRAINT IF EXISTS car_listings_status_check;
        ALTER TABLE car_listings ADD CONSTRAINT car_listings_status_check
            CHECK (status IN ('ACTIVE', 'SOLD', 'DRAFT', 'REMOVED', 'SUSPENDED', 'BLOCKED'));
        ALTER TABLE car_listings ADD COLUMN IF NOT EXISTS moderation_reason TEXT;
        ALTER TABLE car_listings ADD COLUMN IF NOT EXISTS moderated_by UUID;
        ALTER TABLE car_listings ADD COLUMN IF NOT EXISTS moderated_at TIMESTAMPTZ;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS listing_reports
(
    id              UUID PRIMARY KEY,
    car_id          UUID          NOT NULL,
    reporter_id     UUID          NOT NULL,
    reason          VARCHAR(30)   NOT NULL,
    comment         VARCHAR(1000),
    status          VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    resolved_by     UUID,
    resolved_at     TIMESTAMPTZ,
    resolution_note TEXT
);

-- One open report per user and listing; once resolved or dismissed they may report again
CREATE UNIQUE INDEX IF NOT EXISTS uq_listing_reports_open ON listing_reports (car_id, reporter_id) WHERE status = 'OPEN';
CREATE INDEX IF NOT EXISTS idx_listing_reports_status_car ON listing_reports (status, car_id);
CREATE INDEX IF NOT EXISTS idx_listing_reports_reporter ON listing_reports (reporter_id, created_at);
