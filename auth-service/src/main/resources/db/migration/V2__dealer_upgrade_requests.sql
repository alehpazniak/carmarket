-- Pending USER -> DEALER switches, confirmed through an emailed link
CREATE TABLE IF NOT EXISTS dealer_upgrade_requests (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash    VARCHAR(64) NOT NULL UNIQUE,
    expires_at    TIMESTAMP NOT NULL,
    confirmed_at  TIMESTAMP,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_dealer_upgrade_user ON dealer_upgrade_requests(user_id);
