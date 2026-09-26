CREATE TABLE payments (
    id              UUID PRIMARY KEY,
    session_id      VARCHAR(100) NOT NULL UNIQUE,
    user_id         UUID         NOT NULL,
    product_code    VARCHAR(50)  NOT NULL,
    reference_id    VARCHAR(100),
    amount          INTEGER      NOT NULL,
    currency        VARCHAR(3)   NOT NULL,
    description     VARCHAR(1024) NOT NULL,
    email           VARCHAR(50)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    p24_token       VARCHAR(100),
    p24_order_id    BIGINT,
    p24_method_id   INTEGER,
    p24_statement   VARCHAR(255),
    failure_reason  VARCHAR(255),
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    paid_at         TIMESTAMPTZ,
    refunded_at     TIMESTAMPTZ
);

CREATE INDEX idx_payments_user_id ON payments (user_id, created_at DESC);
CREATE INDEX idx_payments_reference ON payments (product_code, reference_id);
