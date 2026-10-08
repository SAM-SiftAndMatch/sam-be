-- Đồng bộ CHECK constraints do Hibernate tạo với enum hiện tại
-- (ddl-auto=update không tự sửa CHECK cũ khi thêm giá trị enum mới).

ALTER TABLE proposals DROP CONSTRAINT IF EXISTS proposals_status_check;
ALTER TABLE proposals
    ADD CONSTRAINT proposals_status_check
    CHECK (status IN ('PENDING', 'INVITED', 'ACCEPTED', 'REJECTED'));

ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_status_check;
ALTER TABLE payments
    ADD CONSTRAINT payments_status_check
    CHECK (status IN ('PENDING', 'HELD_IN_ESCROW', 'RELEASED', 'REFUNDED', 'EXPIRED'));
