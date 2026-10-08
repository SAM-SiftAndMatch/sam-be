-- Mô hình mới cho phép nhiều đơn mỗi contract (đơn dở EXPIRED, đơn mới thay thế).
-- Trùng lắp được chặn ở tầng service theo trạng thái (HELD/RELEASED), không cần unique này nữa.
DROP INDEX IF EXISTS uq_payments_contract_installment;
