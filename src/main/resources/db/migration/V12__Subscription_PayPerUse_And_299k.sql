-- V12: Pay-per-use gắn 1 project + seed gói AI QA Nâng cao 299k.
-- 1. end_date cho phép NULL (gói lẻ không hạn ngày, sống theo vòng đời project).
-- 2. target_project_id: gói lẻ áp dụng cho project nào (NULL với gói tháng).
-- 3. Seed gói AI QA Nâng cao 299k/tháng (bản nâng cấp của gói Trọng tài Code 59k).
ALTER TABLE user_subscriptions ALTER COLUMN end_date DROP NOT NULL;

ALTER TABLE user_subscriptions
    ADD COLUMN IF NOT EXISTS target_project_id UUID REFERENCES jobs(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_user_subscriptions_target_project
    ON user_subscriptions(target_project_id);

INSERT INTO service_packages
    (id, name, type, price, is_dynamic_price, percentage_fee, description)
VALUES
    ('f0000000-0000-0000-0000-000000000007', 'AI QA Nâng cao', 'SUBSCRIPTION', 299000.00, FALSE, NULL,
     'Bảo hành AI QA cho mọi dự án trong tháng (bản nâng cấp của gói Trọng tài Code)')
ON CONFLICT (id) DO NOTHING;
