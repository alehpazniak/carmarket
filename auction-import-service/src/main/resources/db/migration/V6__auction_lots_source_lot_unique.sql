-- Copart and IAAI lot numbers can collide: lots are unique per (source, lot_number), not by lot_number alone.
-- The old constraint is V1's auction_lots_lot_number_key, or a Hibernate-named one on databases that ddl-auto
-- created before Flyway (baselined), so drop whichever unique constraint covers exactly lot_number.
DO $$
DECLARE
    c RECORD;
BEGIN
    FOR c IN
        SELECT con.conname
        FROM pg_constraint con
        WHERE con.conrelid = 'auction_lots'::regclass
          AND con.contype = 'u'
          AND con.conkey = ARRAY[(SELECT attnum FROM pg_attribute
                                  WHERE attrelid = 'auction_lots'::regclass AND attname = 'lot_number')]
    LOOP
        EXECUTE format('ALTER TABLE auction_lots DROP CONSTRAINT %I', c.conname);
    END LOOP;
END $$;

ALTER TABLE auction_lots
    ADD CONSTRAINT uk_auction_lots_source_lot_number UNIQUE (source, lot_number);

-- SHA-256 (hex) of the normalized mapped fields; a sync only writes a lot when it changed
ALTER TABLE auction_lots ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64);

-- Apibara sends auction_at with an offset; it was stored as a naive TIMESTAMP after dropping the offset
-- (the source's wall clock, in practice UTC). Keep the instant from now on.
ALTER TABLE auction_lots
    ALTER COLUMN auction_date TYPE TIMESTAMPTZ USING auction_date AT TIME ZONE 'UTC';

-- V1 named the element-collection column image_url, the entity maps image_urls (ddl-auto-built databases
-- already have image_urls), so a fresh database failed validation.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'auction_lot_images' AND column_name = 'image_url')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_name = 'auction_lot_images' AND column_name = 'image_urls') THEN
        ALTER TABLE auction_lot_images RENAME COLUMN image_url TO image_urls;
    END IF;
END $$;
