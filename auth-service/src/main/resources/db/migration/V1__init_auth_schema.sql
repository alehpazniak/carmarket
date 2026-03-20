-- Auth Service Schema
-- Run with Flyway or manually against auth_db

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS users (
                                     id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    display_name    VARCHAR(255),
    avatar_url      TEXT,
    role            VARCHAR(20) NOT NULL DEFAULT 'USER',
    email_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
    );

CREATE TABLE IF NOT EXISTS oauth_providers (
                                               id               BIGSERIAL PRIMARY KEY,
                                               user_id          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider         VARCHAR(20) NOT NULL,   -- GOOGLE | FACEBOOK
    provider_user_id VARCHAR(255) NOT NULL,
    access_token     TEXT,
    linked_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_provider_user UNIQUE (provider, provider_user_id)
    );

CREATE INDEX idx_users_email        ON users(email);
CREATE INDEX idx_oauth_user_id      ON oauth_providers(user_id);
CREATE INDEX idx_oauth_provider     ON oauth_providers(provider, provider_user_id);