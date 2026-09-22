CREATE TABLE car_listing_equipment (
    car_id          UUID NOT NULL,
    equipment_code  VARCHAR(60) NOT NULL,
    CONSTRAINT pk_car_listing_equipment PRIMARY KEY (car_id, equipment_code),
    CONSTRAINT fk_car_listing_equipment_car FOREIGN KEY (car_id)
        REFERENCES car_listings (id) ON DELETE CASCADE
);

CREATE INDEX idx_car_listing_equipment_car ON car_listing_equipment (car_id);
