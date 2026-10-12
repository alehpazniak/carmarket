-- Accounts blocked in auth-service (user.blocked / user.unblocked): no WebSocket connection, no sending
CREATE TABLE IF NOT EXISTS blocked_users
(
    user_id    UUID PRIMARY KEY,
    reason     TEXT,
    blocked_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
