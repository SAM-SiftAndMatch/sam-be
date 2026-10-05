-- V13: Đối soát VNPay cho mua gói (giống escrow).
-- 1. gateway_txn_ref: map vnp_TxnRef từ IPN về subscription.
-- 2. Trạng thái PENDING (chờ thanh toán) không cần migration (enum lưu STRING).
ALTER TABLE user_subscriptions
    ADD COLUMN IF NOT EXISTS gateway_txn_ref VARCHAR(255);

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_subscriptions_gateway_txn
    ON user_subscriptions(gateway_txn_ref);
