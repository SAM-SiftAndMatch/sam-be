ALTER TABLE proposals
    ADD COLUMN IF NOT EXISTS attachment_url TEXT,
    ADD COLUMN IF NOT EXISTS attachment_name VARCHAR(255);
