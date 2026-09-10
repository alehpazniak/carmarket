CREATE TABLE usa_shipping_to_port (
                                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                       location VARCHAR(150) NOT NULL,
                                       recommended_port VARCHAR(20),
                                       price_usd DECIMAL(12,2),
                                       has_shipping_price BOOLEAN,
                                       available_ports TEXT,
                                       updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
                                       created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_usa_shipping_to_port_location ON usa_shipping_to_port (LOWER(location));
