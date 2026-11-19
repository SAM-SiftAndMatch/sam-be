ALTER TABLE wallet_ledger_entries
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP(6);
