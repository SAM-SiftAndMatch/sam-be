-- V10: Escrow installments cho payments (đợt 1 DEPOSIT, đợt 2 FINAL).
-- Không sửa V1-V9 (Flyway validate).
ALTER TABLE payments ADD COLUMN IF NOT EXISTS installment_no INT NOT NULL DEFAULT 1;

CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_contract_installment
    ON payments(contract_id, installment_no);
