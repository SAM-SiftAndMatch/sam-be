CREATE TABLE IF NOT EXISTS contract_revisions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_id UUID NOT NULL REFERENCES contracts(id) ON DELETE CASCADE,
    edited_by UUID NOT NULL REFERENCES users(id),
    agreed_amount NUMERIC(38, 2),
    terms_and_conditions TEXT,
    created_at TIMESTAMP(6) DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_contract_revisions_contract_id ON contract_revisions(contract_id);
