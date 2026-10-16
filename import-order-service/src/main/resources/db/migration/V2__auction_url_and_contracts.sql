-- Link to the lot page the dealer pasted (shown to the dealer as "Zobacz na aukcji").
-- Document category CONTRACT ("Umowa") needs nothing here: category is a plain VARCHAR(30).
ALTER TABLE import_orders ADD COLUMN auction_url VARCHAR(500);
