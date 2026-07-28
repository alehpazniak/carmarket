CREATE TABLE auction_lots (
                              id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                              vin VARCHAR(17) NOT NULL,
                              make VARCHAR(100) NOT NULL,
                              model VARCHAR(100) NOT NULL,
                              year INTEGER NOT NULL,
                              source VARCHAR(20) NOT NULL,
                              lot_number VARCHAR(100) NOT NULL UNIQUE,
                              auction_price DECIMAL(12,2),
                              buy_now_price DECIMAL(12,2),
                              currency VARCHAR(3) DEFAULT 'USD',
                              damage_type VARCHAR(100),
                              primary_damage VARCHAR(100),
                              secondary_damage VARCHAR(100),
                              odometer INTEGER,
                              odometer_unit VARCHAR(10) DEFAULT 'mi',
                              engine_capacity INTEGER,
                              fuel_type VARCHAR(20),
                              transmission VARCHAR(50),
                              auction_location VARCHAR(100),
                              auction_date TIMESTAMP,
                              sale_date TIMESTAMP,
                              status VARCHAR(20) NOT NULL DEFAULT 'LIVE',
                              raw_data TEXT,
                              created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                              updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE auction_lot_images (
                                    lot_id UUID REFERENCES auction_lots(id) ON DELETE CASCADE,
                                    image_url VARCHAR(500)
);

CREATE TABLE import_calculations (
                                     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                     lot_id UUID NOT NULL REFERENCES auction_lots(id),
                                     auction_price DECIMAL(12,2),
                                     auction_fee DECIMAL(12,2),
                                     us_delivery DECIMAL(12,2),
                                     ocean_freight DECIMAL(12,2),
                                     eu_port_fee DECIMAL(12,2),
                                     excise DECIMAL(12,2),
                                     vat DECIMAL(12,2),
                                     customs_clearance DECIMAL(12,2),
                                     eu_delivery DECIMAL(12,2),
                                     total_usd DECIMAL(12,2),
                                     total_pln DECIMAL(12,2),
                                     exchange_rate DECIMAL(10,4),
                                     excise_rate DECIMAL(5,2),
                                     target_sale_price_pln DECIMAL(12,2),
                                     estimated_repair_cost_pln DECIMAL(12,2),
                                     estimated_profit_pln DECIMAL(12,2),
                                     profit_margin_percent DECIMAL(5,2),
                                     profit_rating VARCHAR(20),
                                     created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE analytics_vehicle_stats (
                                         id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                         make VARCHAR(100) NOT NULL,
                                         model VARCHAR(100) NOT NULL,
                                         year INTEGER,
                                         damage_type VARCHAR(100),
                                         sample_size INTEGER NOT NULL DEFAULT 0,
                                         avg_purchase_price DECIMAL(12,2),
                                         median_purchase_price DECIMAL(12,2),
                                         avg_repair_cost DECIMAL(12,2),
                                         avg_sale_price_pln DECIMAL(12,2),
                                         avg_profit DECIMAL(12,2),
                                         median_profit DECIMAL(12,2),
                                         avg_days_to_sell INTEGER,
                                         loss_rate_percent DECIMAL(5,2),
                                         last_updated TIMESTAMP,
                                         UNIQUE(make, model, year, damage_type)
);

CREATE INDEX idx_auction_lots_vin ON auction_lots(vin);
CREATE INDEX idx_auction_lots_source ON auction_lots(source);
CREATE INDEX idx_auction_lots_status ON auction_lots(status);
CREATE INDEX idx_auction_lots_make_model ON auction_lots(make, model, year);