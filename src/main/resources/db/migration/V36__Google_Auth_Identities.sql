ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

CREATE TABLE user_google_identities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    issuer VARCHAR(100) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    email_at_link VARCHAR(255) NOT NULL,
    linked_at TIMESTAMP(6) NOT NULL DEFAULT now(),
    CONSTRAINT uk_user_google_issuer_subject UNIQUE (issuer, subject)
);
