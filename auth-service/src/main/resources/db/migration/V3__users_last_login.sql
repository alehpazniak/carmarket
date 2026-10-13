-- Set on every OAuth login; shown in the admin panel. Skipped when users doesn't exist yet
-- (a baselined schema without auth tables): Hibernate then creates it from the entity.
DO $$
BEGIN
    IF to_regclass('users') IS NOT NULL THEN
        ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP;
        CREATE INDEX IF NOT EXISTS idx_users_created_at ON users (created_at);
        CREATE INDEX IF NOT EXISTS idx_users_role_active ON users (role, is_active);
    END IF;
END $$;
