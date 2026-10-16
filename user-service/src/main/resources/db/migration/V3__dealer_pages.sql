-- Documentation only: user-service has no Flyway, Hibernate ddl-auto creates this table from DealerPage.
CREATE TABLE IF NOT EXISTS dealer_pages
(
    user_id       UUID PRIMARY KEY,
    slug          VARCHAR(40)  NOT NULL UNIQUE,
    company_name  VARCHAR(120) NOT NULL,
    headline      VARCHAR(150),
    description   TEXT,
    city          VARCHAR(80),
    street        VARCHAR(150),
    postal_code   VARCHAR(10),
    phone         VARCHAR(30),
    website       VARCHAR(255),
    facebook_url  VARCHAR(255),
    instagram_url VARCHAR(255),
    youtube_url   VARCHAR(255),
    logo_url      VARCHAR(500),
    banner_url    VARCHAR(500),
    gallery_urls  JSONB,
    opening_hours JSONB,
    services      JSONB,
    team          JSONB,
    published     BOOLEAN      NOT NULL,
    created_at    TIMESTAMP(6),
    updated_at    TIMESTAMP(6)
);
