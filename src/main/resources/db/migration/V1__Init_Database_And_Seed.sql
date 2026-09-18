CREATE TABLE IF NOT EXISTS skills (
                                      id SERIAL PRIMARY KEY,
                                      name VARCHAR(255) NOT NULL UNIQUE
    );

CREATE TABLE IF NOT EXISTS users (
                                     id UUID PRIMARY KEY,
                                     email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL,
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6)
    );

CREATE TABLE IF NOT EXISTS freelancer_profiles (
                                                   id UUID PRIMARY KEY,
                                                   user_id UUID NOT NULL UNIQUE REFERENCES users(id),
    headline VARCHAR(255),
    bio TEXT,
    hourly_rate NUMERIC(38, 2),
    github_url VARCHAR(255),
    portfolio_url VARCHAR(255)
    );

CREATE TABLE IF NOT EXISTS client_profiles (
                                               id UUID PRIMARY KEY,
                                               user_id UUID NOT NULL UNIQUE REFERENCES users(id),
    company_name VARCHAR(255),
    industry VARCHAR(255),
    website_url VARCHAR(255),
    description TEXT
    );

CREATE TABLE IF NOT EXISTS freelancer_skills (
                                                 freelancer_id UUID NOT NULL REFERENCES users(id),
    skill_id INT NOT NULL REFERENCES skills(id),
    years_of_experience INT,
    PRIMARY KEY (freelancer_id, skill_id)
    );

CREATE TABLE IF NOT EXISTS jobs (
                                    id UUID PRIMARY KEY,
                                    client_id UUID NOT NULL REFERENCES users(id),
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    budget_min NUMERIC(38, 2),
    budget_max NUMERIC(38, 2),
    status VARCHAR(255) NOT NULL,
    deadline TIMESTAMP(6),
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6)
    );

CREATE TABLE IF NOT EXISTS job_skills (
                                          job_id UUID NOT NULL REFERENCES jobs(id),
    skill_id INT NOT NULL REFERENCES skills(id),
    PRIMARY KEY (job_id, skill_id)
    );

CREATE TABLE IF NOT EXISTS ai_job_recommendations (
                                                      id UUID PRIMARY KEY,
                                                      job_id UUID NOT NULL REFERENCES jobs(id),
    freelancer_id UUID NOT NULL REFERENCES users(id),
    match_score NUMERIC(38, 2),
    is_viewed BOOLEAN NOT NULL,
    created_at TIMESTAMP(6)
    );

CREATE TABLE IF NOT EXISTS proposals (
                                         id UUID PRIMARY KEY,
                                         job_id UUID NOT NULL REFERENCES jobs(id),
    freelancer_id UUID NOT NULL REFERENCES users(id),
    cover_letter TEXT,
    proposed_budget NUMERIC(38, 2) NOT NULL,
    estimated_duration_days INT,
    ai_match_score NUMERIC(38, 2),
    status VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6)
    );

CREATE TABLE IF NOT EXISTS contracts (
                                         id UUID PRIMARY KEY,
                                         job_id UUID NOT NULL UNIQUE REFERENCES jobs(id),
    proposal_id UUID NOT NULL UNIQUE REFERENCES proposals(id),
    freelancer_id UUID NOT NULL REFERENCES users(id),
    client_id UUID NOT NULL REFERENCES users(id),
    agreed_amount NUMERIC(38, 2) NOT NULL,
    status VARCHAR(255) NOT NULL,
    started_at TIMESTAMP(6),
    completed_at TIMESTAMP(6)
    );

CREATE TABLE IF NOT EXISTS payments (
                                        id UUID PRIMARY KEY,
                                        contract_id UUID NOT NULL REFERENCES contracts(id),
    amount NUMERIC(38, 2) NOT NULL,
    currency VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    payment_gateway_id VARCHAR(255),
    escrow_held_at TIMESTAMP(6),
    released_at TIMESTAMP(6)
    );

CREATE TABLE IF NOT EXISTS reviews (
                                       id UUID PRIMARY KEY,
                                       contract_id UUID NOT NULL UNIQUE REFERENCES contracts(id),
    reviewer_id UUID NOT NULL REFERENCES users(id),
    reviewee_id UUID NOT NULL REFERENCES users(id),
    rating INT,
    comment TEXT,
    created_at TIMESTAMP(6)
    );

TRUNCATE TABLE reviews, payments, contracts, proposals, ai_job_recommendations, job_skills, jobs, freelancer_skills, client_profiles, freelancer_profiles, users, skills CASCADE;

INSERT INTO skills (id, name) VALUES
                                  (1, 'Java'), (2, 'Spring Boot'), (3, 'React'), (4, 'TypeScript'), (5, 'Figma');

ALTER SEQUENCE skills_id_seq RESTART WITH 6;

INSERT INTO users (id, email, password_hash, full_name, role, is_active, created_at, updated_at) VALUES
                                                                                                     ('11111111-1111-1111-1111-111111111111', 'admin@sam.com', '$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW', 'System Admin', 'ADMIN', true, now(), now()),
                                                                                                     ('22222222-2222-2222-2222-222222222222', 'client@fpt.com', '$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW', 'FPT Software', 'CLIENT', true, now(), now()),
                                                                                                     ('33333333-3333-3333-3333-333333333333', 'long.be@gmail.com', '$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW', 'Long Dương (Backend)', 'FREELANCER', true, now(), now()),
                                                                                                     ('44444444-4444-4444-4444-444444444444', 'long.fe@gmail.com', '$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW', 'Quang Long (Frontend)', 'FREELANCER', true, now(), now());

INSERT INTO client_profiles (id, user_id, company_name, industry, website_url, description) VALUES
    (gen_random_uuid(), '22222222-2222-2222-2222-222222222222', 'FPT Software', 'IT Outsourcing', 'https://fptsoftware.com', 'Tập đoàn công nghệ hàng đầu Việt Nam');

INSERT INTO freelancer_profiles (id, user_id, headline, bio, hourly_rate, github_url, portfolio_url) VALUES
                                                                                                         (gen_random_uuid(), '33333333-3333-3333-3333-333333333333', 'Senior Java/Spring Boot Developer', 'Chuyên gia thiết kế hệ thống Microservices.', 25.00, 'https://github.com/longduong-be', 'https://longduong.dev'),
                                                                                                         (gen_random_uuid(), '44444444-4444-4444-4444-444444444444', 'React & TypeScript Expert', 'Chuyên làm frontend mượt mà, tối ưu UI/UX.', 20.00, 'https://github.com/longduong-fe', 'https://longduong-fe.dev');

INSERT INTO freelancer_skills (freelancer_id, skill_id, years_of_experience) VALUES
                                                                                 ('33333333-3333-3333-3333-333333333333', 1, 3),
                                                                                 ('33333333-3333-3333-3333-333333333333', 2, 2),
                                                                                 ('44444444-4444-4444-4444-444444444444', 3, 2),
                                                                                 ('44444444-4444-4444-4444-444444444444', 4, 2);

INSERT INTO jobs (id, client_id, title, description, budget_min, budget_max, status, deadline, created_at, updated_at) VALUES
                                                                                                                           ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '22222222-2222-2222-2222-222222222222', 'Tuyển gấp Backend Java Spring Boot', 'Cần build hệ thống API thanh toán Escrow', 1000.00, 2000.00, 'OPEN', '2026-12-01 00:00:00', now(), now()),
                                                                                                                           ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '22222222-2222-2222-2222-222222222222', 'Cần 1 bạn Frontend làm UI React', 'Build màn hình Dashboard cho Freelancer', 500.00, 1000.00, 'IN_PROGRESS', '2026-11-01 00:00:00', now(), now());

INSERT INTO job_skills (job_id, skill_id) VALUES
                                              ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 1),
                                              ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 2),
                                              ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 3),
                                              ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 4);

INSERT INTO ai_job_recommendations (id, job_id, freelancer_id, match_score, is_viewed, created_at) VALUES
                                                                                                       (gen_random_uuid(), 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '33333333-3333-3333-3333-333333333333', 0.98, false, now()),
                                                                                                       (gen_random_uuid(), 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '44444444-4444-4444-4444-444444444444', 0.95, true, now());

INSERT INTO proposals (id, job_id, freelancer_id, cover_letter, proposed_budget, estimated_duration_days, ai_match_score, status, created_at) VALUES
                                                                                                                                                  ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '33333333-3333-3333-3333-333333333333', 'Chào sếp, em từng build API thanh toán VNPay rồi, giao việc cho em là yên tâm.', 1500.00, 14, 0.98, 'PENDING', now()),
                                                                                                                                                  ('dddddddd-dddd-dddd-dddd-dddddddddddd', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '44444444-4444-4444-4444-444444444444', 'Em nhận làm Dashboard giá hời, cam kết Responsive 100%.', 800.00, 7, 0.95, 'ACCEPTED', now());

INSERT INTO contracts (id, job_id, proposal_id, freelancer_id, client_id, agreed_amount, status, started_at, completed_at) VALUES
    ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'dddddddd-dddd-dddd-dddd-dddddddddddd', '44444444-4444-4444-4444-444444444444', '22222222-2222-2222-2222-222222222222', 800.00, 'ACTIVE', now(), null);

INSERT INTO payments (id, contract_id, amount, currency, status, payment_gateway_id, escrow_held_at, released_at) VALUES
    (gen_random_uuid(), 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 800.00, 'VND', 'HELD_IN_ESCROW', 'VNPAY_123456789', now(), null);

INSERT INTO reviews (id, contract_id, reviewer_id, reviewee_id, rating, comment, created_at) VALUES
    (gen_random_uuid(), 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', '22222222-2222-2222-2222-222222222222', '44444444-4444-4444-4444-444444444444', 5, 'Bạn Dev này code frontend rất mượt, đúng hẹn!', now());