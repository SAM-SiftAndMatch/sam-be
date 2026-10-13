CREATE TABLE qa_plans (
    id UUID PRIMARY KEY,
    delivery_id UUID NOT NULL UNIQUE REFERENCES project_deliveries(id) ON DELETE CASCADE,
    source_srs TEXT,
    status VARCHAR(30) NOT NULL,
    generated_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6)
);

CREATE TABLE qa_checklist_items (
    id UUID PRIMARY KEY,
    qa_plan_id UUID NOT NULL REFERENCES qa_plans(id) ON DELETE CASCADE,
    sort_order INTEGER NOT NULL,
    requirement_ref VARCHAR(100),
    title VARCHAR(500) NOT NULL,
    test_steps TEXT NOT NULL,
    expected_result TEXT NOT NULL,
    priority VARCHAR(30) NOT NULL,
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    CONSTRAINT uk_qa_checklist_sort UNIQUE (qa_plan_id, sort_order)
);

CREATE INDEX idx_qa_checklist_plan ON qa_checklist_items(qa_plan_id, sort_order);

CREATE TABLE wallet_accounts (
    id UUID PRIMARY KEY,
    owner_id UUID UNIQUE REFERENCES users(id),
    wallet_key VARCHAR(100) NOT NULL UNIQUE,
    wallet_type VARCHAR(30) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'VND',
    available_balance NUMERIC(38, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6)
);

CREATE TABLE project_settlements (
    id UUID PRIMARY KEY,
    contract_id UUID NOT NULL UNIQUE REFERENCES contracts(id),
    delivery_id UUID NOT NULL UNIQUE REFERENCES project_deliveries(id),
    gross_amount NUMERIC(38, 2) NOT NULL,
    freelancer_payout NUMERIC(38, 2) NOT NULL,
    platform_fee NUMERIC(38, 2) NOT NULL,
    deposit_refund NUMERIC(38, 2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    completed_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6)
);

CREATE TABLE wallet_ledger_entries (
    id UUID PRIMARY KEY,
    wallet_id UUID NOT NULL REFERENCES wallet_accounts(id),
    settlement_id UUID NOT NULL REFERENCES project_settlements(id),
    contract_id UUID NOT NULL REFERENCES contracts(id),
    entry_type VARCHAR(50) NOT NULL,
    amount NUMERIC(38, 2) NOT NULL,
    balance_after NUMERIC(38, 2) NOT NULL,
    description VARCHAR(500) NOT NULL,
    created_at TIMESTAMP(6),
    CONSTRAINT ck_wallet_ledger_amount CHECK (amount > 0)
);

CREATE INDEX idx_wallet_ledger_wallet_created
    ON wallet_ledger_entries(wallet_id, created_at DESC);
