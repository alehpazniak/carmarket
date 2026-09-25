-- Street address for the "my profile" contact page. City already exists on user_profiles.
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS street       VARCHAR(100);
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS house_number VARCHAR(20);
