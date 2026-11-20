CREATE TABLE IF NOT EXISTS ai_api_keys (
    id BIGSERIAL PRIMARY KEY,
    label VARCHAR(100),
    key_encrypted TEXT NOT NULL,
    key_masked VARCHAR(24) NOT NULL,
    key_fingerprint VARCHAR(64) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    priority INTEGER NOT NULL DEFAULT 100 CHECK (priority BETWEEN 0 AND 10000),
    fail_count INTEGER NOT NULL DEFAULT 0,
    last_test_at TIMESTAMPTZ,
    last_test_status VARCHAR(20) NOT NULL DEFAULT 'NEVER'
        CHECK (last_test_status IN ('NEVER', 'OK', 'FAIL')),
    last_test_message VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_api_keys_fingerprint UNIQUE (key_fingerprint)
);

CREATE INDEX IF NOT EXISTS idx_ai_api_keys_active_priority
    ON ai_api_keys(is_active, priority ASC, id ASC);

CREATE TABLE IF NOT EXISTS ai_models (
    id BIGSERIAL PRIMARY KEY,
    model_name VARCHAR(120) NOT NULL,
    display_name VARCHAR(120),
    is_active BOOLEAN NOT NULL DEFAULT true,
    priority INTEGER NOT NULL DEFAULT 100 CHECK (priority BETWEEN 0 AND 10000),
    last_test_at TIMESTAMPTZ,
    last_test_status VARCHAR(20) NOT NULL DEFAULT 'NEVER'
        CHECK (last_test_status IN ('NEVER', 'OK', 'FAIL')),
    last_test_message VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_models_name UNIQUE (model_name)
);

CREATE INDEX IF NOT EXISTS idx_ai_models_active_priority
    ON ai_models(is_active, priority ASC, id ASC);

INSERT INTO ai_models (model_name, display_name, is_active, priority)
VALUES
    ('gemini-3.8-flash', 'Gemini 3.8 Flash (Free Tier)', true, 10),
    ('gemini-3.6-flash', 'Gemini 3.6 Flash (Free Tier)', true, 20),
    ('gemini-3.5-flash-lite', 'Gemini 3.5 Flash-Lite (Free Tier · Hạn mức cao)', true, 30),
    ('gemini-4-hydrogen', 'Gemini 4 Hydrogen (Free Tier)', true, 40),
    ('gemma-4-31b-it', 'Gemma 4 31B IT (Free Tier · Dự phòng)', true, 50),
    ('gemma-4-26b-a4b-it', 'Gemma 4 26B A4B IT (Free Tier · Dự phòng)', true, 60)
ON CONFLICT (model_name) DO NOTHING;
