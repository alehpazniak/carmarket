ALTER TABLE car_listings ADD COLUMN IF NOT EXISTS category VARCHAR(30) NOT NULL DEFAULT 'PASSENGER';

CREATE INDEX IF NOT EXISTS idx_car_listings_category ON car_listings (category);
