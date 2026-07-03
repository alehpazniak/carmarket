CREATE TABLE favorites (
                           id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                           user_id     UUID        NOT NULL,
                           car_id      UUID        NOT NULL,
                           created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
                           CONSTRAINT uq_favorites_user_car UNIQUE (user_id, car_id),
                           CONSTRAINT fk_favorites_car FOREIGN KEY (car_id)
                               REFERENCES car_listings (id) ON DELETE CASCADE
);

CREATE INDEX idx_favorites_user_id ON favorites (user_id);
