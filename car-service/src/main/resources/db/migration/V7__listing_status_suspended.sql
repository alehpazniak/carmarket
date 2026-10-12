-- SUSPENDED: the owner's account is blocked (user.blocked); restored to ACTIVE on user.unblocked.
-- ddl-auto created a CHECK constraint listing the enum values and never updates it, so it is
-- recreated here. Skipped when car_listings doesn't exist yet (a baselined schema that only holds
-- another service's tables): Hibernate then creates it with the current enum values.
DO $$
BEGIN
    IF to_regclass('car_listings') IS NOT NULL THEN
        ALTER TABLE car_listings DROP CONSTRAINT IF EXISTS car_listings_status_check;
        ALTER TABLE car_listings ADD CONSTRAINT car_listings_status_check
            CHECK (status IN ('ACTIVE', 'SOLD', 'DRAFT', 'REMOVED', 'SUSPENDED'));
        CREATE INDEX IF NOT EXISTS idx_car_listings_seller_status ON car_listings (seller_id, status);
    END IF;
END $$;
