-- 1. Tạo 3 tài khoản Freelancer test bằng UUID cố định
INSERT INTO users (id, email, password_hash, full_name, role, is_active, created_at, updated_at) VALUES
                                                                                                     ('55555555-5555-5555-5555-555555555555', 'senior.dev@gmail.com', 'hash', 'Senior Dev (5 Năm)', 'FREELANCER', true, now(), now()),
                                                                                                     ('66666666-6666-6666-6666-666666666666', 'mid.dev@gmail.com', 'hash', 'Mid Dev (2 Năm)', 'FREELANCER', true, now(), now()),
                                                                                                     ('77777777-7777-7777-7777-777777777777', 'junior.dev@gmail.com', 'hash', 'Junior Dev (Fresher)', 'FREELANCER', true, now(), now())
    ON CONFLICT (id) DO NOTHING;

-- 2. Tạo Profile với Bio sắc nét để AI đọc và chấm điểm
INSERT INTO freelancer_profiles (id, user_id, headline, bio) VALUES
                                                                 (gen_random_uuid(), '55555555-5555-5555-5555-555555555555', 'Senior Backend Architect', 'Hơn 5 năm thiết kế hệ thống Microservices quy mô lớn. Tích hợp sâu các cổng thanh toán VNPay, MoMo. Chuyên gia Spring Boot và tối ưu hóa truy vấn SQL.'),
                                                                 (gen_random_uuid(), '66666666-6666-6666-6666-666666666666', 'Mid-level Spring Boot', 'Có kinh nghiệm làm RESTful API với Java Spring Boot. Đã từng làm một số dự án quản lý nội bộ. Biết dùng Docker cơ bản.'),
                                                                 (gen_random_uuid(), '77777777-7777-7777-7777-777777777777', 'Thực tập sinh Java', 'Mới ra trường, rất đam mê học hỏi. Đã làm đồ án tốt nghiệp CRUD bằng Spring Boot. Chăm chỉ, chịu khó cày bug đêm.')
    ON CONFLICT (user_id) DO NOTHING;

-- 3. Cấp kỹ năng (Java = ID 1, Spring Boot = ID 2) với số năm kinh nghiệm khác nhau
INSERT INTO freelancer_skills (freelancer_id, skill_id, years_of_experience) VALUES
                                                                                 ('55555555-5555-5555-5555-555555555555', 1, 5), ('55555555-5555-5555-5555-555555555555', 2, 5),
                                                                                 ('66666666-6666-6666-6666-666666666666', 1, 2), ('66666666-6666-6666-6666-666666666666', 2, 2),
                                                                                 ('77777777-7777-7777-7777-777777777777', 1, 0), ('77777777-7777-7777-7777-777777777777', 2, 0)
    ON CONFLICT (freelancer_id, skill_id) DO NOTHING;

-- 4. Cấp gói PRO DEV cho cả 3 ông để lọt vào "Mắt xanh" của hệ thống khi chạy tính năng Tuyển Gấp
INSERT INTO user_subscriptions (user_id, package_id, start_date, end_date, status)
SELECT u.id, 'f0000000-0000-0000-0000-000000000006', NOW(), NOW() + INTERVAL '30 days', 'ACTIVE'
FROM users u WHERE u.email IN ('senior.dev@gmail.com', 'mid.dev@gmail.com', 'junior.dev@gmail.com')
               AND NOT EXISTS (
    SELECT 1 FROM user_subscriptions us WHERE us.user_id = u.id AND us.package_id = 'f0000000-0000-0000-0000-000000000006'
    );