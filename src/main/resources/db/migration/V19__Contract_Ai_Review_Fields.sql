ALTER TABLE contracts
    ADD COLUMN IF NOT EXISTS ai_suggested_amount NUMERIC(38, 2),
    ADD COLUMN IF NOT EXISTS review_status VARCHAR(20),
    ADD COLUMN IF NOT EXISTS review_extracted_amount NUMERIC(38, 2),
    ADD COLUMN IF NOT EXISTS review_note TEXT,
    ADD COLUMN IF NOT EXISTS client_keep_confirmed BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS freelancer_keep_confirmed BOOLEAN DEFAULT FALSE;
