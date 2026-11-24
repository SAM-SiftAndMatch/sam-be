-- Existing sessions keep their persistent-cookie behavior until their current expiry.
ALTER TABLE sessions ADD COLUMN remember_me BOOLEAN NOT NULL DEFAULT TRUE;
-- New sessions require an explicit opt-in to persistence.
ALTER TABLE sessions ALTER COLUMN remember_me SET DEFAULT FALSE;
