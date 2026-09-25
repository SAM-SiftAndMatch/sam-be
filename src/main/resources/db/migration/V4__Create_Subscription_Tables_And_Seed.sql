-- 1. TẠO BẢNG DANH MỤC GÓI DỊCH VỤ (SERVICE_PACKAGES)
CREATE TABLE IF NOT EXISTS service_packages (
                                                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL, -- PAY_PER_USE hoặc SUBSCRIPTION
    price NUMERIC(15, 2),
    is_dynamic_price BOOLEAN NOT NULL DEFAULT FALSE,
    percentage_fee DOUBLE PRECISION,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) DEFAULT NOW(),
    updated_at TIMESTAMP(6) DEFAULT NOW()
    );

-- 2. TẠO BẢNG GIAO DỊCH GÓI THÁNG CỦA USER (USER_SUBSCRIPTIONS)
CREATE TABLE IF NOT EXISTS user_subscriptions (
                                                  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    package_id UUID NOT NULL REFERENCES service_packages(id) ON DELETE CASCADE,
    start_date TIMESTAMP(6) NOT NULL,
    end_date TIMESTAMP(6) NOT NULL,
    status VARCHAR(50) NOT NULL, -- ACTIVE, EXPIRED, CANCELLED
    created_at TIMESTAMP(6) DEFAULT NOW(),
    updated_at TIMESTAMP(6) DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_user_subscriptions_user_id ON user_subscriptions(user_id);
CREATE INDEX IF NOT EXISTS idx_user_subscriptions_status ON user_subscriptions(status);

-- 3. CHÈN DATA GỐC: CÁC GÓI DỊCH VỤ (SEED DATA)
-- Sử dụng UUID cố định để dễ dàng tham chiếu ở bước sau
INSERT INTO service_packages (id, name, type, price, is_dynamic_price, percentage_fee, description) VALUES
                                                                                                        ('f0000000-0000-0000-0000-000000000001', 'Ghim Nổi Bật', 'PAY_PER_USE', 59000.00, FALSE, NULL, 'Ghim dự án lên đầu trang tìm kiếm'),
                                                                                                        ('f0000000-0000-0000-0000-000000000002', 'Tuyển Gấp (AI Headhunter)', 'PAY_PER_USE', 99000.00, FALSE, NULL, 'Bắn thông báo cho 5 Dev xịn nhất'),
                                                                                                        ('f0000000-0000-0000-0000-000000000003', 'Trọng tài Code', 'PAY_PER_USE', 59000.00, FALSE, NULL, 'AI tự động check lỗi code khi Dev bàn giao'),
                                                                                                        ('f0000000-0000-0000-0000-000000000004', 'Bảo hiểm mã nguồn', 'PAY_PER_USE', NULL, TRUE, 3.0, 'Thu phí 3% tổng giá trị dự án để bảo đảm rủi ro'),
                                                                                                        ('f0000000-0000-0000-0000-000000000005', 'Gói BUSINESS', 'SUBSCRIPTION', 249000.00, FALSE, NULL, 'Mở khóa toàn bộ quyền đăng bài nổi bật và tuyển gấp (Dành cho Client)'),
                                                                                                        ('f0000000-0000-0000-0000-000000000006', 'Gói PRO DEV', 'SUBSCRIPTION', 149000.00, FALSE, NULL, 'Mở khóa nhận việc 1 chạm và khiên bảo vệ yêu cầu (Dành cho Freelancer)')
    ON CONFLICT (id) DO NOTHING;

-- 4. CHÈN DATA AN TOÀN: GẮN GÓI CHO NGƯỜI DÙNG (NẾU TỒN TẠI)

-- 4.1 Cấp gói PRO DEV cho tài khoản "long@gmail.com" (Nếu có trong DB)
INSERT INTO user_subscriptions (user_id, package_id, start_date, end_date, status)
SELECT
    u.id,
    'f0000000-0000-0000-0000-000000000006', -- ID gói PRO DEV
    NOW(),
    NOW() + INTERVAL '30 days',
    'ACTIVE'
FROM users u WHERE u.email = 'long@gmail.com'
-- Đảm bảo không cấp đúp nếu chạy script nhiều lần
               AND NOT EXISTS (
    SELECT 1 FROM user_subscriptions us WHERE us.user_id = u.id AND us.package_id = 'f0000000-0000-0000-0000-000000000006'
    );

-- 4.2 Cấp gói BUSINESS cho tài khoản "longclient@gmail.com" (Nếu có trong DB)
INSERT INTO user_subscriptions (user_id, package_id, start_date, end_date, status)
SELECT
    u.id,
    'f0000000-0000-0000-0000-000000000005', -- ID gói BUSINESS
    NOW(),
    NOW() + INTERVAL '30 days',
    'ACTIVE'
FROM users u WHERE u.email = 'longclient@gmail.com'
               AND NOT EXISTS (
    SELECT 1 FROM user_subscriptions us WHERE us.user_id = u.id AND us.package_id = 'f0000000-0000-0000-0000-000000000005'
    );

-- 4.3 (Optional) Cấp thêm gói PRO DEV cho tài khoản mẫu "long.be@gmail.com" ở file V1 để chắc chắn luôn có data test
INSERT INTO user_subscriptions (user_id, package_id, start_date, end_date, status)
SELECT
    u.id,
    'f0000000-0000-0000-0000-000000000006',
    NOW(),
    NOW() + INTERVAL '30 days',
    'ACTIVE'
FROM users u WHERE u.email = 'long.be@gmail.com'
               AND NOT EXISTS (
    SELECT 1 FROM user_subscriptions us WHERE us.user_id = u.id AND us.package_id = 'f0000000-0000-0000-0000-000000000006'
    );