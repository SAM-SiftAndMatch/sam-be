ALTER TABLE contracts ALTER COLUMN proposal_id DROP NOT NULL;

ALTER TABLE contracts
    ADD COLUMN terms_and_conditions TEXT,
    ADD COLUMN revision_limit INT DEFAULT 2,
    ADD COLUMN client_agreed BOOLEAN DEFAULT FALSE,
    ADD COLUMN freelancer_agreed BOOLEAN DEFAULT FALSE;