-- Documentation only: user-service has no Flyway, Hibernate ddl-auto adds these columns from DealerPage.
-- Both set or both NULL (checked by DealerPageService); NULL = not on the dealer map.
ALTER TABLE dealer_pages ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION;
ALTER TABLE dealer_pages ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION;
CREATE INDEX IF NOT EXISTS idx_dealer_pages_location ON dealer_pages (latitude, longitude);
