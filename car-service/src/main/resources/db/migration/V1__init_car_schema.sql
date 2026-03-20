-- Car Service Schema

CREATE TABLE IF NOT EXISTS car_listings (
                                            id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_id       UUID NOT NULL,
    make            VARCHAR(100) NOT NULL,
    model           VARCHAR(100) NOT NULL,
    year            INT NOT NULL,
    price           NUMERIC(12, 2) NOT NULL,
    mileage         INT,
    fuel_type       VARCHAR(20),
    transmission    VARCHAR(20),
    color           VARCHAR(50),
    description     TEXT,
    city            VARCHAR(100),
    country         VARCHAR(100),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
    );

CREATE INDEX idx_car_listings_seller  ON car_listings(seller_id);
CREATE INDEX idx_car_listings_status  ON car_listings(status);
CREATE INDEX idx_car_listings_make    ON car_listings(make);
CREATE INDEX idx_car_listings_price   ON car_listings(price);
CREATE INDEX idx_car_listings_year    ON car_listings(year);