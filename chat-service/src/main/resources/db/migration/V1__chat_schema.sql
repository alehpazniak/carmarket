CREATE TABLE conversations
(
    id              UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    car_id          UUID        NOT NULL,
    buyer_id        UUID        NOT NULL,
    seller_id       UUID        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_message_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_conversation_car_buyer UNIQUE (car_id, buyer_id)
);

CREATE INDEX idx_conversations_buyer ON conversations (buyer_id);
CREATE INDEX idx_conversations_seller ON conversations (seller_id);

CREATE TABLE messages
(
    id              UUID PRIMARY KEY       DEFAULT gen_random_uuid(),
    conversation_id UUID          NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    sender_id       UUID          NOT NULL,
    content         VARCHAR(2000) NOT NULL,
    read_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_messages_conversation ON messages (conversation_id, created_at);