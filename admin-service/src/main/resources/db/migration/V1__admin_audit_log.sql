-- One row per admin.audit event; id is the event's eventId, so a redelivered event is a no-op.
CREATE TABLE admin_audit_log
(
    id          UUID PRIMARY KEY,
    occurred_at TIMESTAMPTZ  NOT NULL,
    actor_id    VARCHAR(64)  NOT NULL,
    actor_email VARCHAR(320),
    service     VARCHAR(64)  NOT NULL,
    action      VARCHAR(64)  NOT NULL,
    target_type VARCHAR(64),
    target_id   VARCHAR(128),
    reason      TEXT,
    details     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    received_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_admin_audit_target ON admin_audit_log (target_type, target_id);
CREATE INDEX idx_admin_audit_actor ON admin_audit_log (actor_id);
CREATE INDEX idx_admin_audit_occurred ON admin_audit_log (occurred_at DESC);
