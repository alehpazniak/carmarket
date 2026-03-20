-- User Service Schema

CREATE TABLE IF NOT EXISTS user_profiles (
                                             id              UUID PRIMARY KEY,          -- matches auth-service users.id
                                             email           VARCHAR(255) NOT NULL UNIQUE,
    display_name    VARCHAR(255),
    avatar_url      TEXT,
    phone_number    VARCHAR(30),
    city            VARCHAR(100),
    country         VARCHAR(100),
    bio             VARCHAR(500),
    role            VARCHAR(20) NOT NULL DEFAULT 'USER',
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    listings_count  INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
    );

CREATE INDEX idx_user_profiles_email ON user_profiles(email);