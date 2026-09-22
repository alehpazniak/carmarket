-- Copy of user contact data, fed by the user.registered Kafka topic.
CREATE TABLE recipients
(
    user_id      UUID PRIMARY KEY,
    email        VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Outbox of emails to send. A row is PENDING until send_at, then SENT (or FAILED).
CREATE TABLE notifications
(
    id           UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    type         VARCHAR(50)  NOT NULL,
    recipient_id UUID         NOT NULL,
    dedup_key    VARCHAR(100) NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    send_at      TIMESTAMPTZ  NOT NULL,
    sent_at      TIMESTAMPTZ,
    attempts     INT          NOT NULL DEFAULT 0,
    last_error   VARCHAR(500),
    payload      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_due ON notifications (send_at) WHERE status = 'PENDING';
CREATE INDEX idx_notifications_key ON notifications (type, recipient_id, dedup_key, status);
