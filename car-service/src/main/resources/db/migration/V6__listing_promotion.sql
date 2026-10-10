-- Paid promotion (payment.completed from payment-service). NULL = not promoted / not highlighted.
ALTER TABLE car_listings ADD COLUMN IF NOT EXISTS promoted_until    TIMESTAMP NULL;
ALTER TABLE car_listings ADD COLUMN IF NOT EXISTS highlighted_until TIMESTAMP NULL;

CREATE INDEX IF NOT EXISTS idx_car_listings_promoted_until ON car_listings (promoted_until);

-- One row per processed payment: makes payment.completed / payment.refunded idempotent and
-- remembers how many days were actually added, so a refund subtracts exactly that.
-- days_applied = 0 when the payment was rejected (listing missing, not ACTIVE or not the buyer's).
CREATE TABLE IF NOT EXISTS listing_promotion_payments
(
    payment_id   UUID PRIMARY KEY,
    car_id       UUID        NOT NULL,
    product_code VARCHAR(50) NOT NULL,
    days_applied INT         NOT NULL,
    applied_at   TIMESTAMP   NOT NULL,
    refunded_at  TIMESTAMP   NULL
);

CREATE INDEX IF NOT EXISTS idx_listing_promotion_payments_car ON listing_promotion_payments (car_id);
