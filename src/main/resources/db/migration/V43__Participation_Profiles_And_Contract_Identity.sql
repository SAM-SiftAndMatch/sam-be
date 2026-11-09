ALTER TABLE users
    ADD COLUMN contract_name VARCHAR(255),
    ADD COLUMN contact_email VARCHAR(255),
    ADD COLUMN phone_number VARCHAR(32),
    ADD COLUMN policy_accepted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN policy_version VARCHAR(32);

ALTER TABLE contracts
    ADD COLUMN client_name VARCHAR(255),
    ADD COLUMN client_email VARCHAR(255),
    ADD COLUMN client_phone VARCHAR(32),
    ADD COLUMN freelancer_name VARCHAR(255),
    ADD COLUMN freelancer_email VARCHAR(255),
    ADD COLUMN freelancer_phone VARCHAR(32),
    ADD COLUMN contract_date DATE,
    ADD COLUMN platform_terms TEXT,
    ADD COLUMN policy_version VARCHAR(32);

-- Preserve existing contracts without attributing new policy acceptance to either party.
UPDATE contracts c SET
    client_name = a.full_name,
    freelancer_name = b.full_name,
    contract_date = c.started_at::date
FROM users a, users b WHERE c.client_id = a.id AND c.freelancer_id = b.id;
