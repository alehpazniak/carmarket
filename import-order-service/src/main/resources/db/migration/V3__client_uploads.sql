-- Clients upload documents too (e.g. a payment confirmation) from /zlecenie/{code}. They have no
-- user id, so uploaded_by is null for their files and uploaded_by_client marks them.
ALTER TABLE import_order_files ALTER COLUMN uploaded_by DROP NOT NULL;
ALTER TABLE import_order_files ADD COLUMN uploaded_by_client BOOLEAN NOT NULL DEFAULT FALSE;
