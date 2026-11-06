-- A bank edit creates an encrypted revision; old ciphertext and withdrawal snapshots stay intact.
ALTER TABLE finance_bank_accounts ADD COLUMN replaced_by UUID REFERENCES finance_bank_accounts(id);
ALTER TABLE finance_bank_accounts ADD CONSTRAINT ck_finance_bank_revision CHECK(replaced_by IS NULL OR (status='DISABLED' AND replaced_by<>id));
CREATE INDEX ix_finance_income_owner_accepted ON finance_payables(owner_id,accepted_at DESC,id DESC);
