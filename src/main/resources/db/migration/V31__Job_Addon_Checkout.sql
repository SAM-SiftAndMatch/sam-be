ALTER TABLE jobs ADD COLUMN client_request_id UUID;
CREATE UNIQUE INDEX uq_jobs_client_request ON jobs(client_id, client_request_id)
    WHERE client_request_id IS NOT NULL;

CREATE TABLE job_addon_orders (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES jobs(id),
    client_id UUID NOT NULL REFERENCES users(id),
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    urgent BOOLEAN NOT NULL DEFAULT FALSE,
    featured_fee NUMERIC(15, 2) NOT NULL DEFAULT 0,
    urgent_fee NUMERIC(15, 2) NOT NULL DEFAULT 0,
    total_fee NUMERIC(15, 2) NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    paid_at TIMESTAMP(6),
    CONSTRAINT ck_job_addon_order_status CHECK
        (status IN ('PENDING', 'PAID', 'PAID_UNAPPLIED', 'CANCELLED')),
    CONSTRAINT ck_job_addon_order_selection CHECK (featured OR urgent)
);
CREATE UNIQUE INDEX uq_job_addon_pending ON job_addon_orders(job_id)
    WHERE status = 'PENDING';
CREATE INDEX idx_job_addon_orders_job ON job_addon_orders(job_id, created_at DESC);

CREATE TABLE job_addon_payment_attempts (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES job_addon_orders(id),
    txn_ref VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(24) NOT NULL,
    payment_url TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    paid_at TIMESTAMP(6),
    CONSTRAINT ck_job_addon_attempt_status CHECK
        (status IN ('PENDING', 'PAID', 'FAILED', 'PAID_DUPLICATE'))
);
CREATE INDEX idx_job_addon_attempt_order ON job_addon_payment_attempts(order_id, created_at DESC);

ALTER TABLE user_notifications ADD COLUMN resolved_at TIMESTAMP(6);
