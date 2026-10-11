-- A dealer's order to import a car won at a US auction for a client.
CREATE TABLE import_orders
(
    id                    UUID PRIMARY KEY,
    dealer_id             UUID         NOT NULL,
    -- Denormalized: status of the latest event (occurred_at, then created_at); NULL = no events yet
    status                VARCHAR(30),

    -- Client access: link /zlecenie/<public_code> + password (only the BCrypt hash is stored)
    public_code           VARCHAR(32)  NOT NULL UNIQUE,
    password_hash         VARCHAR(100) NOT NULL,
    -- Bumped on password reset; client tokens carry it and die when it changes
    access_version        INT          NOT NULL DEFAULT 1,
    client_access_enabled BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_attempts       INT          NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,

    -- Lot
    source                VARCHAR(10)  NOT NULL,
    lot_number            VARCHAR(30),
    vin                   VARCHAR(17),
    make                  VARCHAR(60),
    model                 VARCHAR(80),
    year                  INT,
    sale_date             DATE,
    purchase_price_usd    NUMERIC(12, 2),
    auction_location      VARCHAR(200),
    -- From Apibara sale_document
    title_type            VARCHAR(120),
    title_export_ok       BOOLEAN,
    title_registration_ok BOOLEAN,
    -- Apibara vehicle JSON as it was when the order was created
    auction_snapshot      JSONB,

    -- Shipping (all optional)
    payment_deadline      DATE,
    title_received_at     DATE,
    port_of_loading       VARCHAR(120),
    shipping_line         VARCHAR(120),
    container_number      VARCHAR(11),
    booking_number        VARCHAR(60),
    bill_of_lading        VARCHAR(60),
    vessel_name           VARCHAR(120),
    etd                   DATE,
    eta                   DATE,
    destination_port      VARCHAR(120),
    customs_cleared_at    DATE,

    -- People
    client_name           VARCHAR(200),
    client_email          VARCHAR(255),
    client_phone          VARCHAR(40),
    notify_client         BOOLEAN      NOT NULL DEFAULT FALSE,
    dealer_contact_name   VARCHAR(200),
    dealer_contact_phone  VARCHAR(40),
    dealer_contact_email  VARCHAR(255),
    internal_notes        TEXT,

    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    archived_at           TIMESTAMPTZ
);

CREATE INDEX idx_import_orders_dealer ON import_orders (dealer_id, created_at DESC);
CREATE UNIQUE INDEX uq_import_orders_dealer_lot ON import_orders (dealer_id, source, lot_number)
    WHERE lot_number IS NOT NULL;

-- Status timeline. The current status is the event with the latest occurred_at.
CREATE TABLE import_order_events
(
    id            UUID PRIMARY KEY,
    order_id      UUID        NOT NULL REFERENCES import_orders (id) ON DELETE CASCADE,
    status        VARCHAR(30) NOT NULL,
    -- Real date of the step, may be backdated
    occurred_at   TIMESTAMPTZ NOT NULL,
    client_note   TEXT,
    internal_note TEXT,
    created_by    UUID        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_import_order_events_order ON import_order_events (order_id, occurred_at DESC, created_at DESC);

-- Documents and photos; the bytes live in S3 under import-orders/{order_id}/...
CREATE TABLE import_order_files
(
    id             UUID PRIMARY KEY,
    order_id       UUID         NOT NULL REFERENCES import_orders (id) ON DELETE CASCADE,
    kind           VARCHAR(10)  NOT NULL,
    category       VARCHAR(30)  NOT NULL,
    s3_key         VARCHAR(300) NOT NULL UNIQUE,
    original_name  VARCHAR(255) NOT NULL,
    content_type   VARCHAR(50)  NOT NULL,
    size_bytes     BIGINT       NOT NULL,
    client_visible BOOLEAN      NOT NULL DEFAULT FALSE,
    uploaded_by    UUID         NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_import_order_files_order ON import_order_files (order_id, created_at);

-- Client page activity. IPs are truncated (IPv4 /24, IPv6 /48) — no full addresses are stored.
CREATE TABLE import_order_access_log
(
    id         UUID PRIMARY KEY,
    order_id   UUID        NOT NULL REFERENCES import_orders (id) ON DELETE CASCADE,
    event      VARCHAR(20) NOT NULL,
    at         TIMESTAMPTZ NOT NULL,
    ip_prefix  VARCHAR(50),
    user_agent VARCHAR(200),
    -- jti of the client token, so a VIEW is logged at most once per token per hour
    token_id   VARCHAR(36)
);

CREATE INDEX idx_import_order_access_log_order ON import_order_access_log (order_id, at DESC);
CREATE INDEX idx_import_order_access_log_token ON import_order_access_log (order_id, token_id, event, at);
